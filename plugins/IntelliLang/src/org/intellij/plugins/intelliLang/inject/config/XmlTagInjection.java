/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class XmlTagInjection extends AbstractTagInjection<XmlTagInjection, XmlText> {

  public XmlTagInjection() {
    setTagName("<none>");
  }

  public boolean isApplicable(@NotNull XmlText text) {
    final XmlTag context = text.getParentTag();
    return matches(context) && matchXPath(context);
  }

  public String getDisplayName() {
    final String name = getTagName();
    return name.length() > 0 ? name : "*";
  }

  @NotNull
  public List<TextRange> getInjectedArea(final XmlText element) {
    if (myCompiledValuePattern == null) {
      return Collections.singletonList(TextRange.from(0, element.getTextLength()));
    }
    else {
      final List<TextRange> ranges = getMatchingRanges(myCompiledValuePattern.matcher(element.getValue()), 0);
      return ranges.size() > 0 ? ContainerUtil.map(ranges, new Function<TextRange, TextRange>() {
        public TextRange fun(TextRange s) {
          return new TextRange(element.displayToPhysical(s.getStartOffset()), element.displayToPhysical(s.getEndOffset()));
        }
      }) : Collections.<TextRange>emptyList();
    }
  }
}
