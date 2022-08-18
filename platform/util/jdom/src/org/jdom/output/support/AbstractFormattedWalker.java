/*-- 

 Copyright (C) 2012 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in mtsource and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of mtsource code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows 
    these conditions in the documentation and/or other materials 
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the 
 end-user documentation provided with the redistribution and/or in the 
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos 
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many 
 individuals on behalf of the JDOM Project and was originally 
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom.output.support;

import org.jdom.CDATA;
import org.jdom.Content;
import org.jdom.output.EscapeStrategy;
import org.jdom.output.Format;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * This Walker implementation walks a list of Content in a Formatted form of
 * some sort.
 * <p>
 * The JDOM content can be loosely categorised in to 'Text-like' content
 * (consisting of Text, CDATA, and EntityRef), and everything else. This
 * distinction is significant for for this class and it's sub-classes.
 * <p>
 * There will be text manipulation, and some (but not necessarily
 * all) Text-like content will be returned as text() instead of next().
 * <p>
 * The trick in this class is that it deals with the regular content, and
 * delegates the Text-like content to the sub-classes.
 * <p>
 * Subclasses are tasked with analysing chunks of Text-like content in the
 * {@link #analyzeMultiText(MultiText, int, int)}  method. The subclasses are
 * responsible for adding the relevant text content to the suppliedMultiText
 * instance in such a way as to result in the correct format.
 * <p>
 * The Subclass needs to concern itself with only the text portion because this
 * abstract class will ensure the Text-like content is appropriately indented.
 *
 * @author Rolf Lear
 */
public abstract class AbstractFormattedWalker implements Walker {

  /*
   * We use Text instances to return formatted text to the caller.
   * We do not need to validate the Text content... it is 'safe' to
   * not use the default Text class.
   */
  private static final CDATA CDATATOKEN = new CDATA("");

  /**
   * Indicate how text content should be added
   *
   * @author Rolf Lear
   */
  protected enum Trim {
    /**
     * Left Trim
     */
    LEFT,
    /**
     * Right Trim
     */
    RIGHT,
    /**
     * Both Trim
     */
    BOTH,
    /**
     * Trim Both and replace all internal whitespace with a single space
     */
    COMPACT,
    /**
     * No Trimming at all
     */
    NONE
  }

  private static final Iterator<Content> EMPTYIT = new Iterator<Content>() {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Content next() {
      throw new NoSuchElementException("Cannot call next() on an empty iterator.");
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Cannot remove from an empty iterator.");
    }
  };

  /**
   * Collect together the items that constitute formatted Text-like content.
   *
   * @author Rolf Lear
   */
  protected final class MultiText {


    /**
     * This is private so only this abstract class can create instances.
     */
    private MultiText() {
    }

    /**
     * Ensure we have space for at least one more text-like item.
     */
    private void ensurespace() {
      if (mtsize >= mtdata.length) {
        mtdata = Arrays.copyOf(mtdata, mtsize + 1 + (mtsize / 2));
        mttext = Arrays.copyOf(mttext, mtdata.length);
      }
    }

    /**
     * Handle the case where we have been accumulating true text content,
     * and the next item is not more text.
     *
     * @param postspace true if the last char in the text should be a space
     */
    private void closeText() {
      if (mtbuffer.length() == 0) {
        // empty text does not need adding at all.
        return;
      }
      ensurespace();
      mtdata[mtsize] = null;
      mttext[mtsize++] = mtbuffer.toString();
      mtbuffer.setLength(0);
    }

    /**
     * Append some text to the text-like sequence that will be treated as
     * plain XML text (PCDATA). If the last content added to this text-like
     * sequence then this new text will be appended directly to the previous
     * text.
     *
     * @param trim How to prepare the Text content
     * @param text The actual Text content.
     */
    public void appendText(final Trim trim, final String text) {
      final int tlen = text.length();
      if (tlen == 0) {
        return;
      }
      String toadd = null;
      switch (trim) {
        case NONE:
          toadd = text;
          break;
        case BOTH:
          toadd = Format.trimBoth(text);
          break;
        case LEFT:
          toadd = Format.trimLeft(text);
          break;
        case RIGHT:
          toadd = Format.trimRight(text);
          break;
        case COMPACT:
          toadd = Format.compact(text);
          break;
      }
      if (toadd != null) {
        toadd = escapeText(toadd);
        mtbuffer.append(toadd);
        mtgottext = true;
      }
    }

    private String escapeText(final String text) {
      if (escape == null || !fstack.getEscapeOutput()) {
        return text;
      }
      return Format.escapeText(escape, endofline, text);
    }

    private String escapeCDATA(final String text) {
      return text;
    }

    /**
     * Append some text to the text-like sequence that will be treated as
     * CDATA.
     *
     * @param trim How to prepare the CDATA content
     * @param text The actual CDATA content.
     */
    public void appendCDATA(final Trim trim, final String text) {
      // this resets the mtbuffer too.
      closeText();
      String toadd = null;
      switch (trim) {
        case NONE:
          toadd = text;
          break;
        case BOTH:
          toadd = Format.trimBoth(text);
          break;
        case LEFT:
          toadd = Format.trimLeft(text);
          break;
        case RIGHT:
          toadd = Format.trimRight(text);
          break;
        case COMPACT:
          toadd = Format.compact(text);
          break;
      }

      toadd = escapeCDATA(toadd);
      ensurespace();
      // mark this as being CDATA text
      mtdata[mtsize] = CDATATOKEN;
      mttext[mtsize++] = toadd;

      mtgottext = true;
    }

    /**
     * Simple method that ensures the text is processed, regardless of
     * content, and is never escaped.
     *
     */
    private void forceAppend(final String text) {
      mtgottext = true;
      mtbuffer.append(text);
    }

    /**
     * Add some JDOM Content (typically an EntityRef) that will be treated
     * as part of the Text-like sequence.
     *
     * @param c the content to add.
     */
    public void appendRaw(final Content c) {
      closeText();
      ensurespace();
      mttext[mtsize] = null;
      mtdata[mtsize++] = c;
      mtbuffer.setLength(0);
    }

    /**
     * Indicate that there is no further content to be added to the
     * text-like sequence.
     */
    public void done() {
      if (mtpostpad && newlineindent != null) {
        // this will be ignored if there was not some content.
        mtbuffer.append(newlineindent);
      }
      if (mtgottext) {
        closeText();
      }
      mtbuffer.setLength(0);
    }
  }


  private Content pending = null;
  private final Iterator<? extends Content> content;
  private final boolean alltext;
  private final boolean allwhite;
  private final String newlineindent;
  private final String endofline;
  private final EscapeStrategy escape;
  private final FormatStack fstack;
  private boolean hasnext;


  // MultiText handling changed in 2.0.5
  // MultiText is something quite complicated, but it goes something like this:
  // XML Content is either text-like, or its not. If we encounter text-like content
  // then we find out how many text-like contents are in a row, and we add them to a
  // multi-text. We then either get to the end of the content, or a non-text content.
  // If we complete the multitext, we then move on to the non-text item, and we set multitext
  // to null. Both multitect and pendingmt are thus null.
  // If the content following the non-text is then text-like, we populate pendingmt.
  // bottom line is that multitext and pendingmt can never both be set.
  // we use one set of variables to back up both of them. This is fast, and safe in a single
  // threaded environment (which the Walkers are guaranteed to be in).
  // all MultiText-specific variables have the names mt*
  private MultiText multitext = null;
  private MultiText pendingmt = null;
  private final MultiText holdingmt = new MultiText();

  private final StringBuilder mtbuffer = new StringBuilder();
  // if there should be indenting after this text.
  private boolean mtpostpad;
  // indicate whether there is something actually added.
  private boolean mtgottext = false;
  // the number of mixed content values.
  private int mtsize = 0;
  private int mtsourcesize = 0;
  private Content[] mtsource = new Content[8];
  // the location of the processed content.
  private Content[] mtdata = new Content[8];
  // whether the mixed content should be returned as raw JDOM objects
  private String[] mttext = new String[8];

  // the current cursor in the mixed content.
  private int mtpos = -1;
  // we cheat here by using Boolean as a three-state option...
  // we expect it to be null often.
  private Boolean mtwasescape;

  /**
   * Create a Walker that preserves all content in its raw state.
   *
   * @param xx       the content to walk.
   * @param fstack   the current FormatStack
   * @param doescape Whether Text values should be escaped.
   */
  public AbstractFormattedWalker(final List<? extends Content> xx,
                                 final FormatStack fstack, final boolean doescape) {
    super();
    this.fstack = fstack;
    this.content = xx.isEmpty() ? EMPTYIT : xx.iterator();
    this.escape = doescape ? fstack.getEscapeStrategy() : null;
    newlineindent = fstack.getPadBetween();
    endofline = fstack.getLevelEOL();
    if (!content.hasNext()) {
      alltext = true;
      allwhite = true;
    }
    else {
      boolean atext = false;
      boolean awhite = false;
      pending = content.next();
      if (isTextLike(pending)) {
        // the first item in the list is Text-like, and we pre-check
        // to see whether all content is text.... and whether it amounts
        // to something.
        pendingmt = buildMultiText(true);
        analyzeMultiText(pendingmt, 0, mtsourcesize);
        pendingmt.done();

        if (pending == null) {
          atext = true;
          awhite = mtsize == 0;
        }
        if (mtsize == 0) {
          // first content in list is ignorable.
          pendingmt = null;
        }
      }
      alltext = atext;
      allwhite = awhite;
    }
    hasnext = pendingmt != null || pending != null;
  }

  @Override
  public final Content next() {

    if (!hasnext) {
      throw new NoSuchElementException("Cannot walk off end of Content");
    }

    if (multitext != null && mtpos + 1 >= mtsize) {
      // finished this multitext. need to move on.
      multitext = null;
      resetMultiText();
    }
    if (pendingmt != null) {
      // we have a multi-text pending from the last block
      // this will only be the case when the previous value was non-text.
      if (mtwasescape != null &&
          fstack.getEscapeOutput() != mtwasescape.booleanValue()) {
        // we calculated pending with one escape strategy, but it changed...
        // we need to recalculate it....

        mtsize = 0;
        mtwasescape = fstack.getEscapeOutput();
        analyzeMultiText(pendingmt, 0, mtsourcesize);
        pendingmt.done();
      }
      multitext = pendingmt;
      pendingmt = null;
    }

    if (multitext != null) {

      // OK, we have text-like content to push back.
      // and it still has values in it.
      // advance the cursor
      mtpos++;

      final Content ret = mttext[mtpos] == null
                          ? mtdata[mtpos] : null;


      // we can calculate the hasnext
      hasnext = mtpos + 1 < mtsize ||
                pending != null;

      // return null to indicate text content.
      return ret;
    }

    // non-text, increment and return content.
    final Content ret = pending;
    pending = content.hasNext() ? content.next() : null;

    // OK, we are returning some content.
    // we need to determine the state of the next loop.
    // cursor at this point has been advanced!
    if (pending == null) {
      hasnext = false;
    }
    else {
      // there is some more content.
      // we need to inspect it to determine whether it is good
      if (isTextLike(pending)) {
        // calculate what this next text-like content looks like.
        pendingmt = buildMultiText(false);
        analyzeMultiText(pendingmt, 0, mtsourcesize);
        pendingmt.done();

        if (mtsize > 0) {
          hasnext = true;
        }
        else {
          // all white text... perhaps we need indenting anyway.
          // buildMultiText has moved on the pending value....
          if (pending != null && newlineindent != null) {
            // yes, we need indenting.
            // redefine the pending.
            resetMultiText();
            pendingmt = holdingmt;
            pendingmt.forceAppend(newlineindent);
            pendingmt.done();
            hasnext = true;
          }
          else {
            pendingmt = null;
            hasnext = pending != null;
          }
        }
      }
      else {
        // it is non-text content... we have more content.
        // but, we just returned non-text content. We may need to indent
        if (newlineindent != null) {
          resetMultiText();
          pendingmt = holdingmt;
          pendingmt.forceAppend(newlineindent);
          pendingmt.done();
        }
        hasnext = true;
      }
    }
    return ret;
  }

  private void resetMultiText() {
    mtsourcesize = 0;
    mtpos = -1;
    mtsize = 0;
    mtgottext = false;
    mtpostpad = false;
    mtwasescape = null;
    mtbuffer.setLength(0);
  }

  /**
   * Add the content at the specified indices to the provided MultiText.
   *
   * @param mtext  the MultiText to append to.
   * @param offset The first Text-like content to add to the MultiText
   * @param len    The number of Text-like content items to add.
   */
  protected abstract void analyzeMultiText(MultiText mtext, int offset, int len);

  /**
   * Get the content at a position in the input content. Useful for subclasses
   * in their {@link #analyzeMultiText(MultiText, int, int)} calls.
   *
   * @param index the index to get the content at.
   * @return the content at the index.
   */
  protected final Content get(final int index) {
    return mtsource[index];
  }

  @Override
  public final boolean isAllText() {
    return alltext;
  }

  @Override
  public final boolean hasNext() {
    return hasnext;
  }

  /**
   * This method was changed in 2.0.5
   * It now is only called when building the content of the variable pendingmt
   * This is important, because only pendingmt can be referenced when analyzing
   * the MultiText content.
   *
   * @return The updated MultiText containing the correct sequence of Text-like content
   */
  private MultiText buildMultiText(final boolean first) {
    // set up a sequence where the next bunch of stuff is text.
    if (!first && newlineindent != null) {
      mtbuffer.append(newlineindent);
    }
    mtsourcesize = 0;
    do {
      if (mtsourcesize >= mtsource.length) {
        mtsource = Arrays.copyOf(mtsource, mtsource.length * 2);
      }
      mtsource[mtsourcesize++] = pending;
      pending = content.hasNext() ? content.next() : null;
    }
    while (pending != null && isTextLike(pending));

    mtpostpad = pending != null;
    mtwasescape = fstack.getEscapeOutput();
    return holdingmt;
  }

  @Override
  public final String text() {
    if (multitext == null || mtpos >= mtsize) {
      return null;
    }
    return mttext[mtpos];
  }

  @Override
  public final boolean isCDATA() {
    if (multitext == null || mtpos >= mtsize) {
      return false;
    }
    if (mttext[mtpos] == null) {
      return false;
    }

    return mtdata[mtpos] == CDATATOKEN;
  }

  @Override
  public final boolean isAllWhitespace() {
    return allwhite;
  }

  private static boolean isTextLike(final Content c) {
    switch (c.getCType()) {
      case Text:
      case CDATA:
      case EntityRef:
        return true;
      default:
        // nothing.
    }
    return false;
  }
}
