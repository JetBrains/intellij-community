package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.openapi.project.Project;

class AllInPackageInfo extends TestInfoImpl {
  private String myName;

  public void readPacketFrom(final ObjectReader reader) {
    myName = reader.readLimitedString();
  }

  public String getComment() {
    return "";
  }

  public String getName() {
    return myName.length() > 0 ? myName : JUnitConfiguration.DEFAULT_PACKAGE_NAME;
  }

  public Location getLocation(final Project project) {
    return null;
  }
}

