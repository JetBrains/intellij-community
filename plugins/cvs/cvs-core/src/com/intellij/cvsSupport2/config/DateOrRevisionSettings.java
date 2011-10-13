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
package com.intellij.cvsSupport2.config;

import com.intellij.openapi.cvsIntegration.DateOrRevision;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * author: lesya
 */
public class DateOrRevisionSettings implements JDOMExternalizable, DateOrRevision, Cloneable, Comparable<DateOrRevisionSettings> {
  public String BRANCH = "";
  public String DATE = "";
  public boolean USE_BRANCH = false;
  public boolean USE_DATE = false;

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DateOrRevisionSettings)) return false;

    final DateOrRevisionSettings dateOrRevisionSettings = (DateOrRevisionSettings)o;

    if (USE_BRANCH != dateOrRevisionSettings.USE_BRANCH) {
      return false;
    }
    if (USE_BRANCH) {
      if (!BRANCH.equals(dateOrRevisionSettings.BRANCH)) {
        return false;
      }
    }
    if (USE_DATE != dateOrRevisionSettings.USE_DATE) {
      return false;
    }
    if (USE_DATE) {
      if (!DATE.equals(dateOrRevisionSettings.DATE)) {
        return false;
      }
    }
    return true;
  }

  public int hashCode() {
    int result;
    result = USE_BRANCH ? 1 : 0;
    if (USE_BRANCH) {
      result = BRANCH.hashCode();
    }
    result = 29 * result + (USE_DATE ? 1 : 0);
    if (USE_DATE) {
      result = 29 * result + DATE.hashCode();
    }
    return result;
  }

  @Override
  public boolean shouldUseDate() {
    return USE_DATE;
  }

  @Override
  public boolean shouldUseBranch() {
    return USE_BRANCH;
  }

  @Override
  public String getBranch() {
    return BRANCH;
  }

  @Override
  public String getDate() {
    if (DATE == null) return "";
    return DATE;
  }

  public void setDate(String value) {
    if (value == null)
      DATE = "";
    else
      DATE = value;
  }

  public boolean overridesDefault() {
    return USE_BRANCH || USE_DATE;
  }

  public DateOrRevisionSettings updateFrom(DateOrRevision dateOrRevision) {
    USE_BRANCH = dateOrRevision.shouldUseBranch();
    USE_DATE = dateOrRevision.shouldUseDate();
    BRANCH = dateOrRevision.getBranch();
    DATE = dateOrRevision.getDate();
    return this;
  }

  @Override
  public DateOrRevisionSettings clone() throws CloneNotSupportedException {
    return (DateOrRevisionSettings)super.clone();
  }

  @Override
  public int compareTo(final DateOrRevisionSettings dateOrRevision) {
    if (USE_DATE && dateOrRevision.USE_DATE && DATE != null && dateOrRevision.DATE != null) {
      return DATE.compareTo(dateOrRevision.DATE);
    }

    return 0;
  }

  public String asString() {
    if (USE_DATE) {
      return DATE;
    }
    if (USE_BRANCH) {
      return BRANCH;
    }
    return "HEAD";
  }
}
