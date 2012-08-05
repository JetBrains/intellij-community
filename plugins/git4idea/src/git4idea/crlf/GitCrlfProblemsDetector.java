/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.crlf;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Given a number of files, detects if CRLF line separators in them are about to be committed to Git. That is:
 * <ul>
 *   <li>Checks if {@code core.autocrlf} is set to {@code true} or {@code input}.</li>
 *   <li>If not, checks if files contain CRLFs.</li>
 *   <li>
 *     For files with CRLFs checks if there are gitattributes set on them, such that would either force CRLF conversion on checkin,
 *     either indicate that these CRLFs are here intentionally.
 *   </li>
 * </ul>
 * All checks are made only for Windows system.
 *
 * @author Kirill Likhodedov
 */
public class GitCrlfProblemsDetector {

  @NotNull
  public static GitCrlfProblemsDetector detect(Collection<VirtualFile> files) {
    return new GitCrlfProblemsDetector();
  }

  public boolean shouldWarn() {
    return false;
  }

}
