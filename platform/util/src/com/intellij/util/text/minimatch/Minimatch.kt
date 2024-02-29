/**
 * The MIT License (MIT)

 * Copyright (c) 2015 Angelo

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.intellij.util.text.minimatch

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SmartList
import com.intellij.util.containers.Stack
import com.intellij.util.containers.mapSmart
import java.util.regex.Pattern

private val LOG = logger<Minimatch>()

private val GLOBSTAR = GlobStar()
private val hasBraces = Pattern.compile("\\{.*}")
private val slashSplit = Pattern.compile("/+")

// any single thing other than /
// don't need to escape / when using new RegExp()
private const val QMARK = "[^/]"

// * => any number of characters
private const val STAR = "$QMARK*?"

private const val reSpecials = "().*{}+?[]^$\\!"

/**
 * Provides implementation of linux glob pattern matching.
 *
 * Port of Node.js https://github.com/isaacs/minimatch to Kotlin,
 * originally made by Angelo in https://github.com/angelozerr/minimatch.java
 */
class Minimatch @JvmOverloads constructor(pattern: String, val options: MinimatchOptions = MinimatchOptions()) {
  private val comment: Boolean
  private val empty = false
  val negate: Boolean
  private val set: List<List<ParseItem>>
  private val pattern: String

  init {
    val normalizedPattern = if (options.allowWindowsPaths) replaceEscapedBackSlash(pattern) else pattern
    comment = !options.nocomment && normalizedPattern.startsWith('#')
    // empty patterns and comments match nothing.
    if (comment) {
      this.pattern = normalizedPattern
      set = emptyList()
      negate = false
    }
    else {
      if (options.nonegate) {
        this.pattern = normalizedPattern
        negate = false
      }
      else {
        var negate = false
        var negateOffset = 0
        while (negateOffset < pattern.length && pattern[negateOffset] == '!') {
          negate = !negate
          negateOffset++
        }

        this.negate = negate
        this.pattern = if (negateOffset > 0) pattern.substring(negateOffset) else pattern
      }
      set = make()
    }
  }

  private fun make(): List<List<ParseItem>> {
    // step 2: expand braces
    val set = braceExpand(pattern, options)

    LOG.debug { "$pattern ${set.contentToString()}" }

    // step 3: now we have a set, so turn each one into a series of path-portion matching patterns.
    // These will be regexps, except in the case of "**", which is set to the GLOBSTAR object for globstar behavior, and will not contain any / characters
    val globParts = globParts(set)
    LOG.debug { "$pattern ${toString(globParts)}" }

    // glob --> regexps
    val results = globParts.mapSmart { it.mapSmart { parse(it, false).item } }
    LOG.debug { "$pattern $results" }
    return results
  }

  private fun toString(globParts: List<Array<String>>): CharSequence {
    val sb = StringBuilder()
    sb.append('[')
    for (arr in globParts) {
      sb.append(arr.contentToString())
      sb.append(", ")
    }
    if (sb.length > 1) {
      sb.setLength(sb.length - 2)
    }
    sb.append(']')
    return sb
  }

  private fun globParts(set: Array<String>) = set.mapSmart { slashSplit.split(it, Integer.MAX_VALUE) }

  // parse a component of the expanded set.
  // At this point, no pattern may contain "/" in it
  // so we're going to return a 2d array, where each entry is the full pattern, split on '/', and then turned into a regular expression.
  // A regexp is made at the end which joins each array with an
  // escaped /, and another full one which joins each regexp with |.
  //
  // Following the lead of Bash 4.1, note that "**" only has special meaning
  // when it is the *only* thing in a path portion. Otherwise, any
  // series of * is equivalent to a single *. Globstar behavior is
  // enabled by default, and can be disabled by setting options.noglobstar.
  private fun parse(pattern: String, isSub: Boolean): ParseResult {
    if (!options.noglobstar && "**" == pattern) {
      return ParseResult(GLOBSTAR, false)
    }
    if (pattern.isEmpty()) {
      return ParseResult(ParseItem.Empty, false)
    }

    val ctx = ParseContext()
    ctx.re = ""
    ctx.hasMagic = options.nocase

    var escaping = false
    // ? => one single character
    val patternListStack = Stack<PatternListItem>()
    val negativeListStack = Stack<PatternListItem>()
    var plType: Char

    var inClass = false
    var reClassStart = -1
    var classStart = -1
    // . and .. never match anything that doesn't start with .,
    // even when options.dot is set.
    val patternStart = when {
      pattern[0] == '.' -> "" // anything
      options.dot -> "(?!(?:^|\\/)\\.{1,2}(?:$|\\/))"
      else -> "(?!\\.)"
    }// not (start or / followed by . or .. followed by / or end)

    loop@ for (i in pattern.indices) {
      var c = pattern[i]
      LOG.debug { "$pattern\t$i ${ctx.re} \"$c\"" }

      // skip over any that are escaped.
      if (escaping && reSpecials.contains(c)) {
        ctx.re += "\\" + c
        escaping = false
        continue
      }

      when (c) {
        '/' ->
          // completely not allowed, even escaped.
          // Should already be path-split by now.
          throw IllegalStateException()

        '\\' -> {
          clearStateChar(ctx)
          escaping = true
          continue@loop
        }

      // the various stateChar values for the "extglob" stuff.
        '?', '*', '+', '@', '!' -> {
          LOG.debug { "$pattern\t$i ${ctx.re} \"$c\" <-- stateChar" }

          // all of those are literals inside a class, except that the glob [!a] means [^a] in regexp
          if (inClass) {
            LOG.debug("  in class")
            if (c == '!' && i == classStart + 1) {
              c = '^'
            }
            ctx.re += c
            continue@loop
          }

          // if we already have a stateChar, then it means that there was something like ** or +? in there.
          // Handle the stateChar, then proceed with this one.
          LOG.debug { "call clearStateChar \"${ctx.stateChar}\"" }
          clearStateChar(ctx)
          ctx.stateChar = c
          // if extglob is disabled, then +(asdf|foo) isn't a thing. just clear the statechar *now*, rather than even diving into the patternList stuff.
          if (options.noext) {
            clearStateChar(ctx)
          }
          continue@loop
        }

        '(' -> {
          if (inClass) {
            ctx.re += "("
            continue@loop
          }

          if (ctx.stateChar == null) {
            ctx.re += "\\("
            continue@loop
          }

          plType = ctx.stateChar!!
          patternListStack.push(PatternListItem(plType, i - 1, ctx.re.length))

          // negation is (?:(?!js)[^/]*)
          ctx.re += if (ctx.stateChar == '!') "(?:(?!(?:" else "(?:"
          LOG.debug { "plType \"${ctx.stateChar}\" \"${ctx.re}\"" }
          ctx.stateChar = null
          continue@loop
        }

        ')' -> {
          if (inClass || patternListStack.isEmpty()) {
            ctx.re += "\\)"
            continue@loop
          }

          clearStateChar(ctx)
          ctx.hasMagic = true
          ctx.re += ")"
          val pl = patternListStack.pop()
          plType = pl.type
          // negation is (?:(?!js)[^/]*)
          // The others are (?:<pattern>)<type>
          when (plType) {
            '!' -> {
              negativeListStack.push(pl)
              ctx.re += ")[^/]*?)"
              pl.reEnd = ctx.re.length
            }
            '?', '+', '*' -> ctx.re += plType
          }
          continue@loop
        }

        '|' -> {
          if (inClass || patternListStack.size == 0 || escaping) {
            ctx.re += "\\|"
            escaping = false
            continue@loop
          }

          clearStateChar(ctx)
          ctx.re += '|'
          continue@loop
        }

      // these are mostly the same in regexp and glob
        '[' -> {
          // swallow any state-tracking char before the [
          clearStateChar(ctx)

          if (inClass) {
            ctx.re += "\\$c"
            continue@loop
          }

          inClass = true
          classStart = i
          reClassStart = ctx.re.length
          ctx.re += c
          continue@loop
        }

        ']' -> {
          // a right bracket shall lose its special
          // meaning and represent itself in
          // a bracket expression if it occurs
          // first in the list. -- POSIX.2 2.8.3.2
          if (i == classStart + 1 || !inClass) {
            ctx.re += "\\" + c
            escaping = false
            continue@loop
          }

          // handle the case where we left a class open.
          // "[z-a]" is valid, equivalent to "\[z-a\]"
          if (inClass) {
            // split where the last [ was, make sure we don't have
            // an invalid re. if so, re-walk the contents of the
            // would-be class to re-translate any characters that
            // were passed through as-is
            // TODO: It would probably be faster to determine this
            // without a try/catch and a new RegExp, but it's tricky
            // to do safely. For now, this is safe and works.
            val cs = pattern.substring(classStart + 1, i)
            try {
              Pattern.compile("[$cs]")
            }
            catch (e: Throwable) {
              // not a valid class!
              val sp = parse(cs, true)
              ctx.re = "${ctx.re.substring(0, reClassStart)}\\[${sp.item.source}\\]"
              ctx.hasMagic = ctx.hasMagic || sp.isB
              inClass = false
              continue@loop
            }

          }

          // finish up the class.
          ctx.hasMagic = true
          inClass = false
          ctx.re += c
          continue@loop
        }

        else -> {
          // swallow any state char that wasn't consumed
          clearStateChar(ctx)

          if (escaping) {
            // no need
            escaping = false
          }
          else if (reSpecials.contains(c) && !(c == '^' && inClass)) {
            ctx.re += "\\"
          }

          ctx.re += c
        }
      } // switch
    } // for

    // handle the case where we left a class open.
    // "[abc" is valid, equivalent to "\[abc"
    if (inClass) {
      // split where the last [ was, and escape it
      // this is a huge pita. We now have to re-walk
      // the contents of the would-be class to re-translate
      // any characters that were passed through as-is
      val cs = pattern.substring(classStart + 1)
      val sp = this.parse(cs, true)
      ctx.re = ctx.re.substring(0, reClassStart) + "\\[" + sp.item.source
      ctx.hasMagic = ctx.hasMagic || sp.isB
    }

    // handle the case where we had a +( thing at the *end*
    // of the pattern.
    // each pattern list stack adds 3 chars, and we need to go through
    // and escape any | chars that were passed through as-is for the regexp.
    // Go through and escape them, taking care not to double-escape any
    // | chars that were already escaped.
    while (!patternListStack.isEmpty()) {
      val pl = patternListStack.pop()
      var tail: CharSequence = ctx.re.substring(pl.reStart + 3)
      // maybe some even number of \, then maybe 1 \, followed by a |
      val p = Pattern.compile("((?:\\\\{2})*)(\\\\?)\\|")
      val m = p.matcher(tail)
      val sb = StringBuilder()
      var lastEnd = 0
      while (m.find()) {
        val g1 = m.group(1)
        var g2: String? = m.group(2)
        if (g2.isNullOrEmpty()) {
          // the | isn't already escaped, so escape it.
          g2 = "\\"
        }
        // need to escape all those slashes *again*, without escaping the
        // one that we need for escaping the | character.  As it works out,
        // escaping an even number of slashes can be done by simply repeating
        // it exactly after itself.  That's why this trick works.
        sb.append(tail.substring(lastEnd, m.start()))
        sb.append(g1).append(g1).append(g2).append("|")
        lastEnd = m.end()
      }
      sb.append(tail, lastEnd, tail.length)

      tail = sb

      LOG.debug { "tail=$tail" }
      val t = when (pl.type) {
        '*' -> STAR
        '?' -> QMARK
        else -> "\\" + pl.type
      }

      ctx.hasMagic = true
      ctx.re = ctx.re.substring(0, pl.reStart) + t + "\\(" + tail
    }

    // handle trailing things that only matter at the very end.
    clearStateChar(ctx)
    if (escaping) {
      // trailing \\
      ctx.re += "\\\\"
    }

    // only need to apply the nodot start if the re starts with
    // something that could conceivably capture a dot
    var addPatternStart = false
    when (ctx.re[0]) {
      '.', '[', '(' -> addPatternStart = true
    }

    // Hack to work around lack of negative lookbehind in JS
    // A pattern like: *.!(x).!(y|z) needs to ensure that a name
    // like 'a.xyz.yz' doesn't match.  So, the first negative
    // lookahead, has to look ALL the way ahead, to the end of
    // the pattern.
    while (!negativeListStack.isEmpty()) {
      val nl = negativeListStack.pop()

      val nlBefore = ctx.re.substring(0, nl.reStart)
      val nlFirst = ctx.re.substring(nl.reStart, nl.reEnd - 8)
      var nlLast = ctx.re.substring(nl.reEnd - 8, nl.reEnd)
      var nlAfter = ctx.re.substring(nl.reEnd)

      nlLast += nlAfter

      // Handle nested stuff like *(*.js|!(*.json)), where open parens
      // mean that we should *not* include the ) in the bit that is considered
      // "after" the negated section.
      val openParensBefore = nlBefore.split(Regex("\\(")).dropLastWhile(String::isEmpty).toTypedArray().size - 1
      var cleanAfter = nlAfter
      for (i in 0..openParensBefore - 1) {
        cleanAfter = cleanAfter.replace(Regex("\\)[+*?]?"), "")
      }
      nlAfter = cleanAfter

      var dollar = ""
      if (nlAfter.isEmpty() && !isSub) {
        dollar = "$"
      }
      ctx.re = nlBefore + nlFirst + nlAfter + dollar + nlLast
    }

    // if the re is not "" at this point, then we need to make sure
    // it doesn't match against an empty path part.
    // Otherwise a/* will match a/, which it should not.
    if (!ctx.re.isEmpty() && ctx.hasMagic) {
      ctx.re = "(?=.)${ctx.re}"
    }

    if (addPatternStart) {
      ctx.re = patternStart + ctx.re
    }
    // parsing just a piece of a larger pattern.
    if (isSub) {
      return ParseResult(LiteralItem(ctx.re), ctx.hasMagic)
    }

    // skip the regexp for non-magical patterns
    // unescape anything in it, though, so that it'll be
    // an exact match against a file etc.
    if (!ctx.hasMagic) {
      return ParseResult(LiteralItem(globUnescape(pattern)), false)
    }

    return ParseResult(MagicItem(ctx.re, options), false)
  }

  private fun clearStateChar(ctx: ParseContext) {
    if (ctx.stateChar != null) {
      // we had some state-tracking character
      // that wasn't consumed by this pass.
      when (ctx.stateChar) {
        '*' -> {
          ctx.re += STAR
          ctx.hasMagic = true
        }
        '?' -> {
          ctx.re += QMARK
          ctx.hasMagic = true
        }
        else -> ctx.re += "\\${ctx.stateChar!!}"
      }
      LOG.debug { "clearStateChar \"${ctx.stateChar}\" \"${ctx.re}\"" }
      ctx.stateChar = null
    }
  }

  private fun braceExpand(pattern: String, options: MinimatchOptions) = if (options.nobrace || !hasBraces.matcher(pattern).matches()) arrayOf(pattern) else expand(pattern)

  //XXX - implement brace expansion
  private fun expand(pattern: String) = arrayOf(pattern)

  fun match(path: List<CharSequence>, partial: Boolean = false): Boolean {
    // just ONE of the pattern sets in this.set needs to match
    // in order for it to be valid. If negating, then just one
    // match means that we have failed.
    // Either way, return on the first hit.

    LOG.debug { "$pattern set $set" }

    // Find the basename of the path by looking for the last non-empty segment
    var filename: CharSequence? = null
    var i = path.size - 1
    while (i >= 0) {
      filename = path[i]
      if (!filename.isEmpty()) {
        break
      }
      i--
    }

    i = 0
    while (i < set.size) {
      val pattern = set[i]
      var file = path
      if (options.matchBase && pattern.size == 1) {
        file = SmartList(filename!!)
      }
      if (matchOne(file, pattern, partial)) {
        return options.flipNegate || !negate
      }
      i++
    }

    // didn't get any hits. this is success if it's a negative pattern, failure otherwise.
    if (options.flipNegate) {
      return false
    }
    return negate
  }

  // set partial to true to test if, for example,
  // "/a/b" matches the start of "/*/b/*/d"
  // Partial means, if you run out of file before you run out of pattern, then that's fine, as long as all the parts match.
  private fun matchOne(file: List<CharSequence>, pattern: List<ParseItem>, partial: Boolean): Boolean {
    LOG.debug { "matchOne\n\tOptions: $options\n\tfile: $file\n\tpattern: $pattern" }
    LOG.debug { "matchOne ${file.size} ${pattern.size}" }

    var fi = 0
    var pi = 0
    val fl = file.size
    val pl = pattern.size
    while (fi < fl && pi < pl) {
      val p = pattern[pi]
      val f = file[fi]

      LOG.debug { "$pattern $p $f" }

      if (p is GlobStar) {
        LOG.debug { "GLOBSTAR [$pattern, $p, $f]" }
        // "**"
        // a/**/b/**/c would match the following:
        // a/b/x/y/z/c
        // a/x/y/z/b/c
        // a/b/x/b/x/c
        // a/b/c
        // To do this, take the rest of the pattern after
        // the **, and see if it would match the file remainder.
        // If so, return success.
        // If not, the ** "swallows" a segment, and try again.
        // This is recursively awful.
        //
        // a/**/b/**/c matching a/b/x/y/z/c
        // - a matches a
        // - doublestar
        // - matchOne(b/x/y/z/c, b/**/c)
        // - b matches b
        // - doublestar
        // - matchOne(x/y/z/c, c) -> no
        // - matchOne(y/z/c, c) -> no
        // - matchOne(z/c, c) -> no
        // - matchOne(c, c) yes, hit
        var fr = fi
        val pr = pi + 1
        if (pr == pl) {
          LOG.debug("** at the end")
          // a ** at the end will just swallow the rest.
          // We have found a match.
          // however, it will not swallow /.x, unless
          // options.dot is set.
          // . and .. are *never* matched by **, for explosively
          // exponential reasons.
          while (fi < fl) {
            val item = file[fi]
            if (item == "." || item == ".." || !options.dot && item.startsWith('.')) {
              return false
            }
            fi++
          }
          return true
        }

        // ok, let's see if we can swallow whatever we can.
        while (fr < fl) {
          val swallowee = file[fr]

          LOG.debug { "\nglobstar while $file $fr $pattern $pr $swallowee" }
          // XXX remove this slice. Just pass the start index.
          if (this.matchOne(file.subList(fr, file.size),
            pattern.subList(pr, pattern.size), partial)) {
            LOG.debug { "globstar found match! $fr $fl $swallowee" }
            // found a match
            return true
          }
          else {
            // can't swallow "." or ".." ever.
            // can only swallow ".foo" when explicitly asked.
            if (swallowee == "."
                || swallowee == ".."
                || !options.dot && swallowee.startsWith('.')) {
              LOG.debug { "dot detected! $file $fr $pattern $pr" }
              break
            }

            // ** swallows a segment, and continue.
            LOG.debug("globstar swallow a segment, and continue")
            fr++
          }
        }

        // no match was found.
        // However, in partial mode, we can't say this is necessarily
        // over.
        // If there's more *pattern* left, then
        if (partial) {
          // ran out of file
          LOG.debug { "\n>>> no match, partial? $file $fr $pattern $pr" }
          if (fr == fl) {
            return true
          }
        }
        return false
      }

      // something other than **
      // non-magic patterns just have to match exactly
      // patterns with magic have been turned into regexps.
      if (!p.match(f, options)) {
        LOG.debug { "pattern match $p $f false" }
        return false
      }
      LOG.debug { "pattern match $p $f true" }
      fi++
      pi++
    }
    // Note: ending in / means that we'll get a final ""
    // at the end of the pattern. This can only match a
    // corresponding "" at the end of the file.
    // If the file ends in /, then it can only match a
    // a pattern that ends in /, unless the pattern just
    // doesn't have any more for it. But, a/b/ should *not*
    // match "a/b/*", even though "" matches against the
    // [^/]*? pattern, except in partial mode, where it might
    // simply not be reached yet.
    // However, a/b/ should still satisfy a/*

    // now either we fell off the end of the pattern, or we're done.
    if (fi == fl && pi == pl) {
      // ran out of pattern and filename at the same time.
      // an exact hit!
      return true
    }
    else if (fi == fl) {
      // ran out of file, but still had pattern left.
      // this is ok if we're doing the match as part of
      // a glob fs traversal.
      return partial
    }
    else if (pi == pl) {
      // ran out of pattern, still have file left.
      // this is only acceptable if we're on the very last
      // empty segment of a file with a trailing slash.
      // a/* should match a/b/
      return fi == fl - 1 && file[fi].isEmpty()
    }

    // should be unreachable
    throw IllegalStateException("")
  }

  @JvmOverloads
  fun match(input: String, partial: Boolean = false): Boolean = match(input, DefaultPathAdapter, partial)

  @JvmOverloads
  fun <T> match(input: T, adapter: PathAdapter<T>, partial: Boolean = false): Boolean {
    LOG.debug { "match $input $pattern" }

    if (comment) {
      return false
    }

    val file = adapter.toArray(input, options)
    if (empty) {
      return file.isEmpty()
    }

    LOG.debug { "$pattern split $file" }
    return match(file, partial)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other?.javaClass != javaClass) return false

    other as Minimatch

    if (options != other.options) return false
    if (comment != other.comment) return false
    if (empty != other.empty) return false
    if (negate != other.negate) return false
    if (pattern != other.pattern) return false

    return true
  }

  override fun hashCode(): Int {
    var result = options.hashCode()
    result = 31 * result + comment.hashCode()
    result = 31 * result + empty.hashCode()
    result = 31 * result + negate.hashCode()
    result = 31 * result + pattern.hashCode()
    return result
  }

  override fun toString(): String = pattern
}

@JvmOverloads
fun minimatch(p: String, pattern: String, options: MinimatchOptions = MinimatchOptions()): Boolean {
  // "" only matches ""
  if (pattern.isBlank()) {
    return p.isEmpty()
  }

  if (!options.nocomment && pattern.startsWith('#')) {
    return false
  }

  return Minimatch(pattern, options).match(p)
}

interface PathAdapter<in T> {
  /**
   * Converts the given path to an array. Note that if path has a
   * trailing separator (it denotes a directory), the last item in
   * the array should be an empty String.
   * @return path converted to array of its segments
   */
  fun toArray(path: T, options: MinimatchOptions): List<String>
}

object DefaultPathAdapter : PathAdapter<String> {
  override fun toArray(path: String, options: MinimatchOptions): List<String> = (if (options.allowWindowsPaths) replaceEscapedBackSlash(path) else path).split('/')
}

// replace "\\" by "/"
fun replaceEscapedBackSlash(s: String): String = s.replace("\\\\", "/")

private val unescape = Pattern.compile("\\\\(.)")

// replace stuff like \* with *
fun globUnescape(s: String): String = unescape.matcher(s).replaceAll("$1")

fun minimatchAll(path: List<CharSequence>, patterns: List<Minimatch>): Boolean {
  var match = false
  for (pattern in patterns) {
    // If we've got a match, only re-test for exclusions.
    // if we don't have a match, only re-test for inclusions.
    if (match != pattern.negate) {
      continue
    }

    match = pattern.match(path)
  }
  return match
}
