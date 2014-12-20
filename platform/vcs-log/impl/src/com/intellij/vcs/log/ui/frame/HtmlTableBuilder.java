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
package com.intellij.vcs.log.ui.frame;

public class HtmlTableBuilder {
  private final StringBuilder myStringBuilder = new StringBuilder();

  public HtmlTableBuilder() {
    myStringBuilder.append("<table>");
  }

  public HtmlTableBuilder startRow() {
    myStringBuilder.append("<tr>");
    return this;
  }

  public HtmlTableBuilder endRow() {
    myStringBuilder.append("</tr>");
    return this;
  }

  public HtmlTableBuilder append(String value) {
    myStringBuilder.append("<td>");
    myStringBuilder.append(value);
    myStringBuilder.append("</td>");
    return this;
  }

  public HtmlTableBuilder append(String value, String align) {
    myStringBuilder.append("<td align=\"").append(align).append("\">");
    myStringBuilder.append(value);
    myStringBuilder.append("</td>");
    return this;
  }

  public String build() {
    myStringBuilder.append("</table>");
    return myStringBuilder.toString();
  }
}
