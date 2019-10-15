// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

/**
 * Corrects some simple but popular mistakes on the author format.
 * The required format is: `author name <author.name@email.com>`
 */
internal object GitCommitAuthorCorrector {
  fun correct(authorValue: String): String {
    var author = authorValue.trim()
    val openBrace = author.indexOf('<')
    val closeBrace = author.indexOf('>')

    if (openBrace < 0) { // email should open with "<"
      val at = author.lastIndexOf("@")
      if (at < 0) return author

      val email = author.lastIndexOf(' ', at - 1)
      if (email < 0) return author

      author = author.substring(0, email + 1) + "<" + author.substring(email + 1)
    }
    else if (openBrace > 0 && author[openBrace - 1] != ' ') { // insert space before email
      author = author.substring(0, openBrace) + " " + author.substring(openBrace)
    }

    if (closeBrace < 0) { // email should close with ">"
      author += ">"
    }

    return author
  }
}
