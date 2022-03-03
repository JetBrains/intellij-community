package com.intellij.grazie.ide.language.xml;

import com.intellij.application.options.CodeStyle;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  protected @Nullable TextContent buildTextContent(@NotNull PsiElement element,
                                                   @NotNull Set<TextContent.TextDomain> allowedDomains) {
    IElementType type = PsiUtilCore.getElementType(element);
    if (isText(type) && hasSuitableDialect(element)) {
      var classifier = tagClassifier(element);
      PsiElement container = SyntaxTraverser.psiApi().parents(element)
        .find(e -> e instanceof XmlDocument || e instanceof XmlTag && classifier.apply((XmlTag)e) != TagKind.Inline);
      if (container != null) {
        Map<PsiElement, TextContent> contentsInside = CachedValuesManager.getCachedValue(container, () ->
          CachedValueProvider.Result.create(calcContents(container), container));
        return contentsInside.get(element);
      }
    }

    if (type == XmlTokenType.XML_COMMENT_CHARACTERS && allowedDomains.contains(COMMENTS) && hasSuitableDialect(element)) {
      return builder.build(element, COMMENTS);
    }

    if (type == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN && allowedDomains.contains(LITERALS) && hasSuitableDialect(element)) {
      return builder.build(element, LITERALS);
    }

    return null;
  }

  private @NotNull Map<PsiElement, TextContent> calcContents(PsiElement container) {
    if (container instanceof XmlTag && isNonText((XmlTag)container)) {
      return Collections.emptyMap();
    }

    var classifier = tagClassifier(container);
    var unknownContainer = container instanceof XmlTag && classifier.apply((XmlTag) container) == TagKind.Unknown;

    var fullContent = NotNullLazyValue.lazy(() -> TextContent.psiFragment(PLAIN_TEXT, container));

    var visitor = new PsiRecursiveElementWalkingVisitor() {
      final Map<PsiElement, TextContent> result = new HashMap<>();
      final List<PsiElement> group = new ArrayList<>();
      boolean unknownBefore = unknownContainer;

      @Override
      public void visitElement(@NotNull PsiElement each) {
        if (each instanceof XmlTag) {
          TagKind kind = classifier.apply((XmlTag)each);
          if (kind != TagKind.Inline) {
            boolean unknown = kind == TagKind.Unknown;
            flushGroup(unknown);
            unknownBefore = unknown;
            return;
          }
        }
        
        if (isText(PsiUtilCore.getElementType(each))) {
          group.add(each);
        }
        super.visitElement(each);
      }

      private void flushGroup(boolean unknownAfter) {
        int containerStart = container.getTextRange().getStartOffset();
        TextContent content = TextContent.join(ContainerUtil.map(group, e ->
          extractRange(fullContent.getValue(), e.getTextRange().shiftLeft(containerStart))
        ));
        if (content != null) {
          if (unknownBefore) content = content.markUnknown(TextRange.from(0, 0));
          if (unknownAfter) content = content.markUnknown(TextRange.from(content.length(), 0));
          content = content.removeIndents(Set.of(' ', '\t')).trimWhitespace();
          if (content != null) {
            for (PsiElement e : group) {
              result.put(e, content);
            }
          }
        }
        group.clear();
      }
    };
    container.acceptChildren(visitor);
    visitor.flushGroup(unknownContainer);
    return visitor.result;
  }

  private static TextContent extractRange(TextContent full, TextRange range) {
    return full.excludeRange(new TextRange(range.getEndOffset(), full.length())).excludeRange(new TextRange(0, range.getStartOffset()));
  }

  private static boolean isText(IElementType type) {
    return type == XmlTokenType.XML_WHITE_SPACE || type == TokenType.WHITE_SPACE ||
           type == XmlTokenType.XML_CHAR_ENTITY_REF || type == XmlTokenType.XML_DATA_CHARACTERS;
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

    private static final Set<String> DEFINITELY_BLOCK_TAGS =
      Set.of("body", "p", "br", "td", "li", "title", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "table");

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
        if (DEFINITELY_BLOCK_TAGS.contains(name)) return TagKind.Block;
        if (inlineTags.contains(name)) return TagKind.Inline;
        return TagKind.Unknown;
      };
    }
  }

  private enum TagKind { Block, Inline, Unknown }
}
