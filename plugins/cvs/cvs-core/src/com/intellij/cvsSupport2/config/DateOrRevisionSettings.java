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
public class DateOrRevisionSettings implements JDOMExternalizable, DateOrRevision, Cloneable{
  public String BRANCH = "";
  public String DATE = "";
  public boolean USE_BRANCH = false;
  public boolean USE_DATE = false;

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DateOrRevisionSettings)) return false;

    final DateOrRevisionSettings dateOrRevisionSettings = (DateOrRevisionSettings)o;

    if (USE_BRANCH != dateOrRevisionSettings.USE_BRANCH) return false;
    if (USE_DATE != dateOrRevisionSettings.USE_DATE) return false;
    if (BRANCH != null ? !BRANCH.equals(dateOrRevisionSettings.BRANCH) : dateOrRevisionSettings.BRANCH != null) return false;
    if (DATE != null ? !DATE.equals(dateOrRevisionSettings.DATE) : dateOrRevisionSettings.DATE != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (BRANCH != null ? BRANCH.hashCode() : 0);
    result = 29 * result + (DATE != null ? DATE.hashCode() : 0);
    result = 29 * result + (USE_BRANCH ? 1 : 0);
    result = 29 * result + (USE_DATE ? 1 : 0);
    return result;
  }

  public boolean shouldUseDate() {
    return USE_DATE;
  }

  public boolean shouldUseBranch() {
    return USE_BRANCH;
  }

  public String getBranch() {
    return BRANCH;
  }

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

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

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
