/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diff.ex;

public class DiffFragment {
  private final String myText1;
  private final String myText2;
  private boolean myIsModified;

  public DiffFragment(String text1, String text2) {
    myText1 = text1;
    myText2 = text2;
    myIsModified = (text1 == null || text2 == null || !text1.equals(text2));
  }

  /**
   * Makes sence if both texts are not null
   * @return true if both texts are considered modified, false otherwise
   */
  public boolean isModified() {
    return myIsModified;
  }

  public void setModified(boolean modified) {
    myIsModified = modified;
  }
  
  /**
   * null if absent
   */
  public String getText1() {
    return myText1;
  }
  
  /**
   * null if absent
   */
  public String getText2() {
    return myText2;
  }

  /**
   * Same as {@link #isModified()}, but doesn't require texts checked for null.
   * @return true iff both texts are present and {@link #isModified()}
   */
  public boolean isChange() {
    return myText1 != null && myText2 != null && isModified();
  }

  /**
   * @return true iff both texts are present and not {@link #isModified()}
   */
  public boolean isEqual() {
    return myText1 != null && myText2 != null && !isModified();
  }

  public static DiffFragment unchanged(String text1, String text2) {
    DiffFragment result = new DiffFragment(text1, text2);
    result.setModified(false);
    return result;
  }

  public boolean isOneSide() {
    return myText1 == null || myText2 == null;
  }
}
