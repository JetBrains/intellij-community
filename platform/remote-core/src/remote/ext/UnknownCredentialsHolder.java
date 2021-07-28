// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Irina.Chernushina on 7/29/2016.
 */
public class UnknownCredentialsHolder {
  private @NotNull String mySdkId;

  private @Nullable Element myElement;

  public UnknownCredentialsHolder() {
    mySdkId = "";
  }

  public @NotNull String getSdkId() {
    return mySdkId;
  }

  public void setSdkId(@NotNull String sdkId) {
    mySdkId = sdkId;
  }

  public void save(@NotNull Element element) {
    if (myElement != null) {
      JDOMUtil.copyMissingContent(myElement, element);
    }
  }

  public void load(@NotNull Element element) {
    myElement = element.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UnknownCredentialsHolder holder = (UnknownCredentialsHolder)o;
    return Objects.equals(mySdkId, holder.mySdkId) &&
           Objects.equals(myElement, holder.myElement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mySdkId, myElement);
  }
}
