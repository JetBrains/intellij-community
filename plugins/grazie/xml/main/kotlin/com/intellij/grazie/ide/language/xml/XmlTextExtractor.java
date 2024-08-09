package com.intellij.grazie.ide.language.xml;

import com.intellij.application.options.CodeStyle;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContent.Exclusion;
import com.intellij.grazie.text.TextContent.ExclusionKind;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.HtmlUtilsKt;
import com.intellij.lang.Language;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

import static com.intellij.grazie.text.TextContent.TextDomain.*;

public class XmlTextExtractor extends TextExtractor {
  private static final TextContentBuilder builder = TextContentBuilder.FromPsi.removingIndents(" \t").removingLineSuffixes(" \t");
  private final Set<Class<? extends Language>> myEnabledDialects;

  protected XmlTextExtractor(Class<? extends Language>... enabledDialects) {
    myEnabledDialects = Set.of(enabledDialects);
  }

  protected Function<XmlTag, TagKind> tagClassifier(@NotNull PsiElement context) {
    return __ -> TagKind.Unknown;
  }

  @Override
  protected @NotNull List<TextContent> buildTextContents(@NotNull PsiElement element, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (isText(element) && hasSuitableDialect(element)) {
      var classifier = tagClassifier(element);
      PsiElement container = SyntaxTraverser.psiApi().parents(element)
        .find(e -> e instanceof XmlDocument || e instanceof XmlTag && classifier.apply((XmlTag)e) != TagKind.Inline);
      if (container != null) {
        Map<PsiElement, List<TextContent>> contentsInside = CachedValuesManager.getCachedValue(container, () ->
          CachedValueProvider.Result.create(calcContents(container), container));
        return contentsInside.getOrDefault(element, List.of());
      }
    }

    IElementType type = PsiUtilCore.getElementType(element);
    if (type == XmlTokenType.XML_COMMENT_CHARACTERS && allowedDomains.contains(COMMENTS) && hasSuitableDialect(element)) {
      return ContainerUtil.createMaybeSingletonList(builder.build(element, COMMENTS));
    }

    if (type == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN && allowedDomains.contains(LITERALS) && hasSuitableDialect(element)) {
      TextContent content = builder.build(element, LITERALS);
      if (content != null && seemsNatural(content)) {
        return List.of(content);
      }
    }

    return List.of();
  }

  private @NotNull Map<PsiElement, List<TextContent>> calcContents(PsiElement container) {
    if (container instanceof XmlTag && isNonText((XmlTag)container)) {
      return Collections.emptyMap();
    }

    var classifier = tagClassifier(container);
    var unknownContainer = container instanceof XmlTag && classifier.apply((XmlTag) container) == TagKind.Unknown;

    var fullContent = NotNullLazyValue.lazy(() -> TextContent.psiFragment(PLAIN_TEXT, container));

    var visitor = new PsiRecursiveElementWalkingVisitor() {
      final Map<PsiElement, List<TextContent>> result = new HashMap<>();
      final List<PsiElement> group = new ArrayList<>();
      final Set<Integer> markupIndices = new HashSet<>();
      final Set<Integer> unknownIndices = new HashSet<>();
      final Set<XmlTag> inlineTags = new HashSet<>();
      boolean unknownBefore = unknownContainer;

      @Override
      public void visitElement(@NotNull PsiElement each) {
        if (each instanceof XmlTag tag) {
          TagKind kind = classifier.apply(tag);
          if (kind != TagKind.Inline) {
            boolean unknown = kind == TagKind.Unknown;
            flushGroup(unknown);
            unknownBefore = unknown;
            return;
          } else {
            inlineTags.add(tag);
            markupIndices.add(group.size());
          }
        }
        if (each instanceof OuterLanguageElement || each instanceof XmlEntityRef) {
          flushGroup(true);
          unknownBefore = true;
        }

        if (isText(each)) {
          if (isCdata(each.getParent())) {
            List<TextContent> contents = HtmlUtilsKt.excludeHtml(
              extractRange(each.getTextRange().shiftLeft(container.getTextRange().getStartOffset())));
            if (!contents.isEmpty()) { // isolate CDATA into its own TextContent set for now; maybe glue to the surrounding texts later
              flushGroup(false);
              result.put(each, contents);
              unknownBefore = false;
            }
          } else {
            group.add(each);
          }
        }
        else if (PsiUtilCore.getElementType(each) == XmlTokenType.XML_CHAR_ENTITY_REF) {
          if (HtmlUtilsKt.isSpaceEntity(each.getText())) {
            group.add(each);
          } else {
            unknownIndices.add(group.size());
          }
        }
        super.visitElement(each);
      }

      private TextContent extractRange(TextRange range) {
        TextContent full = fullContent.getValue();
        return full.excludeRange(new TextRange(range.getEndOffset(), full.length())).excludeRange(new TextRange(0, range.getStartOffset()));
      }

      @Override
      protected void elementFinished(PsiElement element) {
        super.elementFinished(element);
        if (inlineTags.contains(element)) {
          markupIndices.add(group.size());
        }
      }

      private void flushGroup(boolean unknownAfter) {
        int containerStart = container.getTextRange().getStartOffset();
        List<TextContent> components = new ArrayList<>(group.size());
        for (int i = 0; i < group.size(); i++) {
          PsiElement e = group.get(i);
          TextContent component = extractRange(e.getTextRange().shiftLeft(containerStart));
          component = applyExclusions(i, component, markupIndices, ExclusionKind.markup);
          component = applyExclusions(i, component, unknownIndices, ExclusionKind.unknown);
          components.add(component);
        }
        TextContent content = TextContent.join(components);
        if (content != null) {
          if (unknownBefore) content = content.markUnknown(TextRange.from(0, 0));
          if (unknownAfter) content = content.markUnknown(TextRange.from(content.length(), 0));
          content = HtmlUtilsKt.inlineSpaceEntities(content.removeIndents(Set.of(' ', '\t')));
          if (content != null) {
            for (PsiElement e : group) {
              result.put(e, List.of(content));
            }
          }
        }
        group.clear();
      }

      private static TextContent applyExclusions(int index, TextContent component, Set<Integer> indices, ExclusionKind kind) {
        if (indices.contains(index)) {
          component = component.excludeRanges(List.of(new Exclusion(0, 0, kind)));
        }
        if (indices.contains(index + 1)) {
          component = component.excludeRanges(List.of(new Exclusion(component.length(), component.length(), kind)));
        }
        return component;
      }
    };
    container.acceptChildren(visitor);
    visitor.flushGroup(unknownContainer);
    return visitor.result;
  }

  private static boolean seemsNatural(TextContent content) {
    return content.toString().contains(" ");
  }

  private static boolean isText(PsiElement leaf) {
    PsiElement parent = leaf.getParent();
    if (!(parent instanceof XmlText) && !isCdata(parent) && !(parent instanceof XmlDocument)) {
      return false;
    }

    IElementType type = PsiUtilCore.getElementType(leaf);
    return type == XmlTokenType.XML_WHITE_SPACE || type == TokenType.WHITE_SPACE ||
           type == XmlTokenType.XML_DATA_CHARACTERS;
  }

  private static boolean isCdata(PsiElement element) {
    return PsiUtilCore.getElementType(element) == XmlElementType.XML_CDATA;
  }

  private boolean hasSuitableDialect(@NotNull PsiElement element) {
    return myEnabledDialects.contains(element.getContainingFile().getLanguage().getClass());
  }

  private static final Set<String> NON_TEXT_TAGS = Set.of("code", "pre");

  private static boolean isNonText(XmlTag tag) {
    return tag instanceof HtmlTag && NON_TEXT_TAGS.contains(tag.getName());
  }

  public static class Xml extends XmlTextExtractor {
    public Xml() {
      super(XMLLanguage.class, XHTMLLanguage.class, DTDLanguage.class);
    }
  }

  public static class Html extends XmlTextExtractor {
    public Html() {
      super(HTMLLanguage.class);
    }

    @Override
    protected @NotNull List<TextContent> buildTextContents(@NotNull PsiElement element,
                                                           @NotNull Set<TextContent.TextDomain> allowedDomains) {
      if (PsiUtilCore.getElementType(element) == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN &&
          element.getParent() instanceof XmlAttributeValue value &&
          value.getParent() instanceof XmlAttribute attr &&
          "class".equalsIgnoreCase(attr.getName())) {
        return List.of();
      }

      return super.buildTextContents(element, allowedDomains);
    }

    @Override
    protected Function<XmlTag, TagKind> tagClassifier(@NotNull PsiElement context) {
      if (!Registry.is("grazie.html.concatenate.inline.tag.contents")) {
        return super.tagClassifier(context);
      }

      HtmlCodeStyleSettings settings = CodeStyle.getCustomSettings(context.getContainingFile(), HtmlCodeStyleSettings.class);
      Set<String> inlineTags = ContainerUtil.newHashSet(settings.HTML_INLINE_ELEMENTS.split(","));
      return tag -> {
        String name = tag.getName();
        if (NON_TEXT_TAGS.contains(name)) return TagKind.Unknown;
        if (HtmlUtilsKt.commonBlockElements.contains(name)) return TagKind.Block;
        if (inlineTags.contains(name)) return TagKind.Inline;
        return TagKind.Unknown;
      };
    }
  }

  protected enum TagKind { Block, Inline, Unknown }
}
