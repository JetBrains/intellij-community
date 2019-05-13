/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.checkin;

import org.jetbrains.annotations.NotNull;

/**
 * Corrects some simple but popular mistakes on the author format.<p/>
 * The required format is: {@code author name <author.name@email.com>}
 */
class GitCommitAuthorCorrector {

  @NotNull
  public static String correct(@NotNull String author) {
    author = author.trim();

    int openBrace = author.indexOf('<');
    int closeBrace = author.indexOf('>');

    if (openBrace < 0) { // email should open with "<"
      int at = author.lastIndexOf("@");
      if (at < 0) {
        return author;
      }
      int email = author.lastIndexOf(' ', at - 1);
      if (email < 0) {
        return author;
      }
      author = author.substring(0, email + 1) + "<" + author.substring(email + 1);
    }
    else if (openBrace > 0 && author.charAt(openBrace - 1) != ' ') { // insert space before email
      author = author.substring(0, openBrace) + " " + author.substring(openBrace);
    }

    if (closeBrace < 0) { // email should close with ">"
      author += ">";
    }

    return author;
  }

}
