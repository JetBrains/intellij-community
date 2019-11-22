/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.ui;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about one stash.
 */
public class StashInfo {
  @NotNull
  private final String myStash; // stash codename (stash@{1})
  private final String myBranch;
  private final String myMessage;
  private final String myText; // The formatted text representation

  public StashInfo(@NotNull String stash, @Nullable String branch, @NotNull String message) {
    myStash = stash;
    myBranch = branch;
    myMessage = message;

    StringBuilder sb = new StringBuilder();
    sb.append("<html><b><tt>").append(StringUtil.escapeXmlEntities(stash)).append("</tt></b>: ");
    if (branch != null) {
      sb.append("<i>").append(StringUtil.escapeXmlEntities(branch)).append("</i>: ");
    }
    sb.append(StringUtil.escapeXmlEntities(message)).append("</html>");
    myText = sb.toString();
  }

  @Override
  public String toString() {
    return myText;
  }

  @NotNull
  public String getStash() {
    return myStash;
  }

  @Nullable
  public String getBranch() {
    return myBranch;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }

  public String getText() {
    return myText;
  }
}
