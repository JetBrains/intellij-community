// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin;

import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import static com.intellij.vcs.log.util.VcsUserUtil.fromExactString;
import static com.intellij.vcs.log.util.VcsUserUtil.toExactString;

/**
 * Corrects some simple but popular mistakes on the author format.<p/>
 * The required format is: {@code author name <author.name@email.com>}
 */
class GitCommitAuthorCorrector {

  @NotNull
  public static String correct(@NotNull String author) {
    VcsUser user = fromExactString(author);
    if (user == null) {
      return author;
    }
    return toExactString(user);
  }
}
