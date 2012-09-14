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
package com.intellij.openapi.vcs;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/14/12
 * Time: 11:25 AM
 */
public interface CommitMessageI {
  /**
   * @return    <code>true</code> if commit message is checked for spelling errors; <code>false</code> otherwise
   */
  boolean isCheckSpelling();

  /**
   * Allows to define whether commit message should be checked for spelling errors.
   *
   * @param checkSpelling  <code>true</code> if commit message should be checked for spelling errors; <code>false</code> otherwise
   */
  void setCheckSpelling(boolean checkSpelling);

  /**
   * Sets the description for the check-in.
   *
   * @param currentDescription the description text.
   */
  void setCommitMessage(String currentDescription);
}
