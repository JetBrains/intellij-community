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

import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.diff.impl.string.DiffStringBuilder;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class DiffFragment {
  public static DiffFragment[] EMPTY_ARRAY = new DiffFragment[0];

  @Nullable private DiffString myText1;
  @Nullable private DiffString myText2;
  private boolean myIsModified;

  private DiffStringBuilder myBuilder1;
  private DiffStringBuilder myBuilder2;

  @TestOnly
  public DiffFragment(@Nullable String text1, @Nullable String text2) {
    this(DiffString.createNullable(text1), DiffString.createNullable(text2));
  }

  public DiffFragment(@Nullable DiffString text1, @Nullable DiffString text2) {
    myText1 = text1;
    myText2 = text2;
    myIsModified = (text1 == null || text2 == null || !text1.equals(text2));
  }

  public static boolean isEmpty(@NotNull DiffFragment fragment) {
    return StringUtil.length(fragment.getText1()) == 0 &&
           StringUtil.length(fragment.getText2()) == 0;
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

  public void appendText1(@Nullable DiffString str) {
    if (str == null) return;
    if (myBuilder1 != null) {
      myText1 = null;
      myBuilder1.append(str);
      return;
    }

    assert myText1 != null;
    if (DiffString.canInplaceConcatenate(myText1, str)) {
      myText1 = DiffString.concatenate(myText1, str);
      return;
    }

    myBuilder1 = new DiffStringBuilder(myText1.length() + str.length());
    myBuilder1.append(myText1);
    myBuilder1.append(str);
    myText1 = null;
  }

  public void appendText2(@Nullable DiffString str) {
    if (str == null) return;
    if (myBuilder2 != null) {
      myText2 = null;
      myBuilder2.append(str);
      return;
    }

    assert myText2 != null;
    if (DiffString.canInplaceConcatenate(myText2, str)) {
      myText2 = DiffString.concatenate(myText2, str);
      return;
    }

    myBuilder2 = new DiffStringBuilder(myText2.length() + str.length());
    myBuilder2.append(myText2);
    myBuilder2.append(str);
    myText2 = null;
  }

  @Nullable
  public DiffString getText1() {
    if (myBuilder1 == null) return myText1;
    if (myText1 != null) return myText1;
    myText1 = myBuilder1.toDiffString();
    return myText1;
  }

  @Nullable
  public DiffString getText2() {
    if (myBuilder2 == null) return myText2;
    if (myText2 != null) return myText2;
    myText2 = myBuilder2.toDiffString();
    return myText2;
  }

  /**
   * Same as {@link #isModified()}, but doesn't require texts checked for null.
   * @return true iff both texts are present and {@link #isModified()}
   */
  public boolean isChange() {
    return (myText1 != null || myBuilder1 != null) && (myText2 != null || myBuilder2 != null) && isModified();
  }

  /**
   * @return true iff both texts are present and not {@link #isModified()}
   */
  public boolean isEqual() {
    return (myText1 != null || myBuilder1 != null) && (myText2 != null || myBuilder2 != null) && !isModified();
  }

  @TestOnly
  public static DiffFragment unchanged(@Nullable String text1, @Nullable String text2) {
    return unchanged(DiffString.createNullable(text1), DiffString.createNullable(text2));
  }

  public static DiffFragment unchanged(@Nullable DiffString text1, @Nullable DiffString text2) {
    DiffFragment result = new DiffFragment(text1, text2);
    result.setModified(false);
    return result;
  }

  public boolean isOneSide() {
    return (myText1 == null && myBuilder1 == null) || (myText2 == null && myBuilder2 == null);
  }
}
