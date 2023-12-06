package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.htmltools.codeInspection.htmlInspections.htmlAddLabelToForm.CreateLabelFromTextAction;
import com.intellij.htmltools.codeInspection.htmlInspections.htmlAddLabelToForm.CreateNewLabelAction;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.IdRefReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HtmlFormInputWithoutLabelInspection extends HtmlLocalInspectionTool {
  private static final Set<String> ourNonlabelInputTypes =
    Set.of("hidden", "file", "image", "reset", "submit", "button");
  private static final Set<String> ourInputElements = Set.of("input", "textarea", "select");


  @Override
  public @NotNull String getShortName() {
    return "HtmlFormInputWithoutLabel";
  }

  @Override
  protected void checkTag(@NotNull XmlTag tag, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = tag.getContainingFile();
    Language language = file.getLanguage();
    // Support JSP & JSPX but exclude JavaScript Frameworks
    if (!(!(language instanceof HTMLLanguage) || language == HTMLLanguage.INSTANCE)) return;

    if (!HtmlUtil.isHtmlTagContainingFile(tag)) {
      return;
    }
    final String tagName = StringUtil.toLowerCase(tag.getName());
    if (ourInputElements.contains(tagName)) {
      if (tag.getAttribute("aria-label") != null || tag.getAttribute("title") != null) return;
      if ("input".equals(tagName)) {
        XmlAttribute type = tag.getAttribute("type");
        if (type != null) {
          String attrValue = type.getValue();
          if (attrValue != null) {
            if (tag instanceof HtmlTag) attrValue = StringUtil.toLowerCase(attrValue);
            if (ourNonlabelInputTypes.contains(attrValue)) return;
          }
        }
      }
      if (file instanceof XmlFile) {
        HtmlLabelsHolder labelsHolder = HtmlLabelsHolder.getInstance((XmlFile)file);
        boolean hasLabel = false;
        for (XmlAttribute attribute : tag.getAttributes()) {
          if (StringUtil.toLowerCase(attribute.getName()).equals("id") || isImplicitIdAttribute(attribute)) {
            String id = attribute.getValue();
            if (id != null && labelsHolder.hasForValue(id)) {
              hasLabel = true;
              break;
            }
          }
        }
        PsiElement child = tag;
        while (!hasLabel) {
          PsiElement parent = child.getParent();
          if (parent == null) break;
          if (parent instanceof XmlTag && "label".equals(StringUtil.toLowerCase(((XmlTag)parent).getName()))) {
            boolean anotherLabel = false;
            for (PsiElement element : parent.getChildren()) {
              if (element == child) {
                break;
              }
              else if (element instanceof XmlTag && "label".equals(StringUtil.toLowerCase(((XmlTag)element).getName()))) {
                anotherLabel = true;
              }
            }
            hasLabel = !anotherLabel;
          }
          child = parent;
        }
        if (!hasLabel) {
          registerProblem(tag, holder);
        }
      }
    }
  }

  private static boolean isImplicitIdAttribute(@NotNull XmlAttribute attribute) {
    XmlTag parent = attribute.getParent();
    if (parent != null) {
      return attribute.equals(IdRefReference.getImplicitIdRefAttr(parent));
    }
    return false;
  }

  private static void registerProblem(XmlTag tag, ProblemsHolder holder) {
    if (InjectedLanguageManager.getInstance(tag.getProject()).getInjectionHost(tag) != null) return;
    List<LocalQuickFix> fixes = new ArrayList<>();

    Pair<PsiElement, PsiElement> pair = getNearestText(tag, new ForwardIterator());
    if (pair != null) {
      fixes.add(new CreateLabelFromTextAction("html.inspections.create.label.from.text.after.action", false, tag.getName()));
    }
    pair = getNearestText(tag, new BackwardIterator());
    if (pair != null) {
      fixes.add(new CreateLabelFromTextAction("html.inspections.create.label.from.text.before.action", true, tag.getName()));
    }
    fixes.add(new CreateNewLabelAction(tag.getName()));
    PsiElement toRegister = XmlTagUtil.getStartTagNameElement(tag);
    assert toRegister != null;

    InspectionManager manager = holder.getManager();
    ProblemDescriptor descriptor = manager
      .createProblemDescriptor(toRegister, toRegister, HtmlToolsBundle.message("html.inspections.form.input.without.label"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, holder.isOnTheFly(),
                               fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    holder.registerProblem(descriptor);
  }

  private interface ElementIterator {
    @Nullable
    PsiElement getNext(PsiElement element);

    Pair<PsiElement, PsiElement> sortOrderedPair(@NotNull PsiElement left, @NotNull PsiElement right);

    @Nullable
    PsiElement getFirstChild(@NotNull PsiElement element);
  }

  public static final class ForwardIterator implements ElementIterator {
    @Override
    public PsiElement getNext(PsiElement element) {
      return element.getNextSibling();
    }

    @Override
    public Pair<PsiElement, PsiElement> sortOrderedPair(@NotNull PsiElement left, @NotNull PsiElement right) {
      return Pair.create(left, right);
    }

    @Override
    public @Nullable PsiElement getFirstChild(@NotNull PsiElement element) {
      return element.getFirstChild();
    }
  }

  public static final class BackwardIterator implements ElementIterator {
    @Override
    public PsiElement getNext(PsiElement element) {
      return element.getPrevSibling();
    }

    @Override
    public Pair<PsiElement, PsiElement> sortOrderedPair(@NotNull PsiElement left, @NotNull PsiElement right) {
      return Pair.create(right, left);
    }

    @Override
    public @Nullable PsiElement getFirstChild(@NotNull PsiElement element) {
      return element.getLastChild();
    }
  }

  public static @Nullable Pair<PsiElement, PsiElement> getNearestText(PsiElement element, ElementIterator iterator) {
    element = iterator.getNext(element);
    if (!(element instanceof XmlText) || element.getChildren().length == 0) {
      return null;
    }
    element = iterator.getFirstChild(element);
    PsiElement first = null;
    PsiElement last = null;
    boolean textPassed = false;
    for (PsiElement current = element; current != null; current = iterator.getNext(current)) {
      if (current instanceof PsiWhiteSpace && current.getText().contains("\n")) {
        if (textPassed) {
          break;
        }
      }
      else if (!(current instanceof PsiComment)) {
        if (first == null) {
          first = current;
        }
        last = current;
        textPassed = true;
      }
    }

    if (!textPassed) {
      return null;
    }
    return iterator.sortOrderedPair(first, last);
  }
}
