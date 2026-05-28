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
package git4idea;

/**
 * This exception is thrown during parsing of a Git command output, in the case of unexpected output format.
 * The exception is unchecked: if it happens, it is either a format that we don't handle yet (and it should be fixed then), or an error
 * in a specific situation (which also should be handled).
 *
 * @author Kirill Likhodedov
 */
public class GitFormatException extends RuntimeException {
  public GitFormatException(String message) {
    super(message);
  }
}
