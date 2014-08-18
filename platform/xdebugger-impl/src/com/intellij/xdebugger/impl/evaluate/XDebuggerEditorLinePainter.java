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
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredText;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueTextRendererImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class XDebuggerEditorLinePainter extends EditorLinePainter {
  @Override
  public Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project, @NotNull VirtualFile file, int lineNumber) {
    if (!Registry.is("ide.debugger.inline")) {
      return null;
    }

    Map<Pair<VirtualFile, Integer>, Set<XValueNodeImpl>> map = project.getUserData(XVariablesView.DEBUG_VARIABLES);
    if (map != null) {
      Set<XValueNodeImpl> values = map.get(Pair.create(file, lineNumber));
      if (values != null && !values.isEmpty()) {
        ArrayList<LineExtensionInfo> result = new ArrayList<LineExtensionInfo>();
        for (XValueNodeImpl value : values) {
          SimpleColoredText text = new SimpleColoredText();
          XValueTextRendererImpl renderer = new XValueTextRendererImpl(text);
          final XValuePresentation presentation = value.getValuePresentation();
          if (presentation == null) continue;
          if (presentation instanceof XValueCompactPresentation) {
            ((XValueCompactPresentation)presentation).renderValue(renderer, value);
          } else {
            presentation.renderValue(renderer);
          }
          final Color color = new JBColor(new Color(61, 128, 101), new Color(61, 128, 101));
          result.add(new LineExtensionInfo("  " + value.getName() + ": ", color, null, null, Font.PLAIN));
          for (String s : text.getTexts()) {
            result.add(new LineExtensionInfo(s, color, null, null, Font.PLAIN));
          }
        }
        return result;
      }
    }

    return null;
  }
}
