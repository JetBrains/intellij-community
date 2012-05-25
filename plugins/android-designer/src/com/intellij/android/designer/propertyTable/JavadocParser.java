/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.propertyTable;

import com.intellij.codeInsight.documentation.DocumentationManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class JavadocParser {
  private static final String[] TAGS = {"{see ", "{@see ", "{@link "};

  @NotNull
  public static String build(@NotNull String title, @NotNull String javadoc) {
    StringBuilder buffer = new StringBuilder();

    buffer.append("<html><head><style type=\"text/css\">p {margin: 5px 0;}</style></head><body>");
    buffer.append("<p><b>").append(title).append("</b> - ");

    for (String tag : TAGS) {
      javadoc = convertLink(javadoc, tag);
    }

    javadoc = javadoc.replaceAll("<code>", "<i>");
    javadoc = javadoc.replaceAll("</code>", "</i>");

    buffer.append(javadoc);

    return buffer.append("</body></html>").toString();
  }

  private static String convertLink(String javadoc, String tag) {
    StringBuilder buffer = new StringBuilder();
    int length = javadoc.length();
    int start = 0;

    while (true) {
      int index = javadoc.indexOf(tag, start);

      if (index == -1) {
        buffer.append(javadoc.substring(start, length));
        break;
      }
      else {
        buffer.append(javadoc.substring(start, index));

        int linkStart = index + tag.length();
        int end = javadoc.indexOf('}', linkStart);
        String linkValue = javadoc.substring(linkStart, end).trim();
        String href;
        String text;

        int spaceIndex = linkValue.indexOf(' ');
        if (spaceIndex != -1) {
          href = linkValue.substring(0, spaceIndex);
          text = linkValue.substring(spaceIndex + 1);
        }
        else {
          href = text = linkValue;
        }

        text = text.replace('#', '.');

        DocumentationManager.createHyperlink(buffer, href, text, true);

        start = end + 1;
      }
    }

    return buffer.toString();
  }
}