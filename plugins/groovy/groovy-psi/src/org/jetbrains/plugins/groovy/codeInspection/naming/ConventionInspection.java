/*
 * Copyright 2007-2013 Dave Griffith, Bas Leijdekkers
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
package org.jetbrains.plugins.groovy.codeInspection.naming;

import com.intellij.codeInspection.ui.ConventionOptionsPanel;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ConventionInspection extends BaseInspection {

  /**
   * public fields for the DefaultJDomExternalizer
   *
   * @noinspection PublicField, WeakerAccess
   */
  public String m_regex = getDefaultRegex();
  /**
   * @noinspection PublicField, WeakerAccess
   */
  public int m_minLength = getDefaultMinLength();
  /**
   * @noinspection PublicField, WeakerAccess
   */
  public int m_maxLength = getDefaultMaxLength();

  protected Pattern m_regexPattern = Pattern.compile(m_regex);

  @NonNls
  protected abstract String getDefaultRegex();

  protected abstract int getDefaultMinLength();

  protected abstract int getDefaultMaxLength();

  protected String getRegex() {
    return m_regex;
  }

  protected int getMinLength() {
    return m_minLength;
  }

  protected int getMaxLength() {
    return m_maxLength;
  }

  protected boolean isValid(String name) {
    final int length = name.length();
    if (length < m_minLength) {
      return false;
    }
    if (length > m_maxLength) {
      return false;
    }
    if ("SerialVersionUID".equals(name)) {
      return true;
    }
    final Matcher matcher = m_regexPattern.matcher(name);
    return matcher.matches();
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    m_regexPattern = Pattern.compile(m_regex);
  }

  @Override
  public JComponent createOptionsPanel() {
    return new ConventionOptionsPanel(this, "m_minLength", "m_maxLength", "m_regex", "m_regexPattern");
  }
}