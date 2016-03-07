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
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class VagrantBasedCredentialsHolder {
  private static final String VAGRANT_FOLDER = "VAGRANT_FOLDER";
  private static final String MACHINE_NAME = "MACHINE_NAME";

  @NotNull
  private String myVagrantFolder;
  private String myMachineName;

  public VagrantBasedCredentialsHolder() {
    myVagrantFolder = "";
  }

  @Deprecated
  /**
   * @deprecated use constructor supporting multiple machines configuration
   */
  public VagrantBasedCredentialsHolder(@NotNull String folder) {
    myVagrantFolder = folder;
  }

  public VagrantBasedCredentialsHolder(@NotNull String vagrantFolder, @Nullable String machineName) {
    myVagrantFolder = vagrantFolder;
    myMachineName = machineName;
  }

  public void setVagrantFolder(@NotNull String vagrantFolder) {
    myVagrantFolder = vagrantFolder;
  }

  @NotNull
  public String getVagrantFolder() {
    return myVagrantFolder;
  }

  @Nullable
  public String getMachineName() {
    return myMachineName;
  }

  public void setMachineName(String machineName) {
    myMachineName = machineName;
  }

  public void load(Element element) {
    setVagrantFolder(StringUtil.notNullize(element.getAttributeValue(VAGRANT_FOLDER)));
    setMachineName(element.getAttributeValue(MACHINE_NAME));
  }

  public void save(Element element) {
    element.setAttribute(VAGRANT_FOLDER, getVagrantFolder());
    if (StringUtil.isNotEmpty(getMachineName())) {
      element.setAttribute(MACHINE_NAME, getMachineName());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VagrantBasedCredentialsHolder holder = (VagrantBasedCredentialsHolder)o;

    if (!myVagrantFolder.equals(holder.myVagrantFolder)) return false;

    if (StringUtil.isNotEmpty(myMachineName) || StringUtil.isNotEmpty(holder.myMachineName)) {
      return StringUtil.equals(myMachineName, holder.myMachineName);
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myVagrantFolder.hashCode();
    result = 31 * result + (myMachineName != null ? myMachineName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "VagrantBasedCredentialsHolder{" +
           "myVagrantFolder='" + myVagrantFolder + '\'' +
           ", myMachineName='" + myMachineName + '\'' +
           '}';
  }
}
