/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ui.ConventionOptionsPanel;
import com.siyeh.HardcodedMethodConstants;

import javax.swing.*;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NamingConventionBean {
  public String m_regex;
  public int m_minLength;
  public int m_maxLength;

  public NamingConventionBean(String regex, int minLength, int maxLength) {
    m_regex = regex;
    m_minLength = minLength;
    m_maxLength = maxLength;
    initPattern();
  }

  private Pattern m_regexPattern;
  
   public boolean isValid(String name) {
    final int length = name.length();
    if (length < m_minLength) {
      return false;
    }
    if (m_maxLength > 0 && length > m_maxLength) {
      return false;
    }
    if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(name)) {
      return true;
    }
    final Matcher matcher = m_regexPattern.matcher(name);
    return matcher.matches();
  }

  public void initPattern() {
    m_regexPattern = Pattern.compile(m_regex);
  }

  public JComponent createOptionsPanel() {
    return new ConventionOptionsPanel(this, "m_minLength", "m_maxLength", "m_regex", "m_regexPattern");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NamingConventionBean bean = (NamingConventionBean)o;
    return m_minLength == bean.m_minLength &&
           m_maxLength == bean.m_maxLength &&
           Objects.equals(m_regex, bean.m_regex);
  }

  @Override
  public int hashCode() {
    return Objects.hash(m_regex, m_minLength, m_maxLength);
  }
}
