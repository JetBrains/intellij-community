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
package com.intellij.android.designer.inspection;

import com.android.tools.lint.detector.api.Issue;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.designer.inspection.ErrorInfo;
import com.intellij.designer.inspection.QuickFix;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.inspections.lint.*;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class ErrorAnalyzer {
  public static void load(XmlFile xmlFile, RadComponent rootComponent, ProgressIndicator progress) {
    ErrorInfo.clear(rootComponent);

    AndroidLintExternalAnnotator annotator = new AndroidLintExternalAnnotator();
    State state = annotator.collectionInformation(xmlFile);
    if (state != null) {
      state = annotator.doAnnotate(state);
      for (ProblemData problemData : state.getProblems()) {
        Issue issue = problemData.getIssue();
        String message = problemData.getMessage();

        TextRange range = problemData.getTextRange();
        if (range.getStartOffset() == range.getEndOffset()) {
          continue;
        }

        Pair<AndroidLintInspectionBase, HighlightDisplayLevel> pair = AndroidLintUtil.getHighlighLevelAndInspection(issue, xmlFile);
        if (pair == null) {
          continue;
        }

        AndroidLintInspectionBase inspection = pair.getFirst();

        if (inspection != null) {
          HighlightDisplayKey key = HighlightDisplayKey.find(inspection.getShortName());

          if (key != null) {
            PsiElement startElement = xmlFile.findElementAt(range.getStartOffset());
            PsiElement endElement = xmlFile.findElementAt(range.getEndOffset() - 1);

            if (startElement != null && endElement != null && !inspection.isSuppressedFor(startElement)) {
              RadComponent component = findComponent(rootComponent, startElement);
              ErrorInfo errorInfo = new ErrorInfo(message, null, pair.getSecond());
              ErrorInfo.add(component, errorInfo);

              List<QuickFix> designerFixes = errorInfo.getQuickFixes();

              for (AndroidLintQuickFix fix : inspection.public_getQuickFixes(message)) {
                if (fix.isApplicable(startElement, endElement, false)) {
                  designerFixes.add(new QuickFix(fix.getName(), null) {
                    @Override
                    public void run() throws Exception {
                      System.out.println("00000000");
                      // TODO: Auto-generated method stub
                    }
                  });
                }
              }

              for (IntentionAction intention : inspection.getIntentions(startElement, endElement)) {
                designerFixes.add(new QuickFix(intention.getText(), null) {
                  @Override
                  public void run() throws Exception {
                    System.out.println("1111111");
                    // TODO: Auto-generated method stub
                  }
                });
              }

              designerFixes.add(new QuickFix("Disable inspection", null) {
                @Override
                public void run() throws Exception {
                  System.out.println("22222");
                  // TODO: Auto-generated method stub
                }
              });
              designerFixes.add(new QuickFix("Edit '" + inspection.getDisplayName() + "' inspection settings", null) {
                @Override
                public void run() throws Exception {
                  System.out.println("3333333");
                  // TODO: Auto-generated method stub
                }
              });

              SuppressIntentionAction[] suppressActions = inspection.getSuppressActions(startElement);
              if (suppressActions != null) {
                for (SuppressIntentionAction action : suppressActions) {
                  if (action.isAvailable(xmlFile.getProject(), null, startElement)) {
                    designerFixes.add(new QuickFix("Suppress: " + action.getText(), action.getIcon(0)) {
                      @Override
                      public void run() throws Exception {
                        System.out.println("4444444");
                        // TODO: Auto-generated method stub
                      }
                    });
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private static RadComponent findComponent(RadComponent rootComponent, PsiElement element) {
    Pair<XmlTag, XmlAttribute> tagInfo = extractTag(element);
    if (tagInfo.first == null) {
      return rootComponent;
    }

    final XmlTag tag = tagInfo.first;
    final RadComponent[] result = new RadComponent[]{rootComponent};

    rootComponent.accept(new RadComponentVisitor() {
      @Override
      public boolean visit(RadComponent component) {
        if (tag == ((RadViewComponent)component).getTag()) {
          result[0] = component;
          return false;
        }
        return true;
      }

      @Override
      public void endVisit(RadComponent component) {
      }
    }, true);

    return result[0];
  }

  private static Pair<XmlTag, XmlAttribute> extractTag(PsiElement element) {
    XmlTag tag = null;
    XmlAttribute attribute = null;

    while (element != null) {
      if (element instanceof XmlAttribute) {
        attribute = (XmlAttribute)element;
      }
      if (element instanceof XmlTag) {
        tag = (XmlTag)element;
        break;
      }

      element = element.getParent();
    }

    return new Pair<XmlTag, XmlAttribute>(tag, attribute);
  }
}