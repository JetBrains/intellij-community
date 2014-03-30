/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.commands;

import org.jetbrains.annotations.Nullable;

/**
* @author Kirill Likhodedov
*/
public enum GitRemoteProtocol {
  GIT,
  SSH,
  HTTP;

  @Nullable
  public static GitRemoteProtocol fromUrl(@Nullable String url) {
    if (url == null) {
      return null;
    }
    url = url.toLowerCase();
    if (url.startsWith("http")) {
      return HTTP;
    }
    if (url.startsWith("git://")) { // "://" are there not to mix with scp-like syntax used for SSH: git@host.com/path/to.git
      return GIT;
    }
    return SSH;
  }

}
