package com.intellij.htmltools.xml.util;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.html.impl.providers.HtmlAttributeValueProvider;
import com.intellij.html.impl.util.MicrodataUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.paths.GlobalPathReferenceProvider;
import com.intellij.openapi.paths.PathReferenceManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.io.URLUtil;
import com.intellij.xml.util.HtmlUtil;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.index.ImageInfoIndex;
import org.intellij.images.util.ImageInfo;
import org.intellij.images.util.ImageInfoReader;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.doIfNotNull;

public final class HtmlReferenceProvider extends PsiReferenceProvider {
  private static final @NonNls String NAME_ATTR_LOCAL_NAME = "name";
  private static final @NonNls String USEMAP_ATTR_NAME = "usemap";
  private static final @NonNls String FOR_ATTR_NAME = "for";
  private static final @NonNls String HREF_ATTRIBUTE_NAME = "href";
  private static final @NonNls String SRC_ATTR_NAME = "src";
  private static final @NonNls String JAVASCRIPT_PREFIX = "javascript:";

  public static final @NotNull NotNullLazyValue<FileType[]> IMAGE_FILE_TYPES =
    NotNullLazyValue.lazy(() -> new FileType[]{ImageFileTypeManager.getInstance().getImageFileType()});
  public static final String LABELLEDBY = "aria-labelledby";

  public static ElementFilter getFilter() {
    return new ElementFilter() {
      @Override
      public boolean isAcceptable(Object _element, PsiElement context) {
        PsiElement element = (PsiElement)_element;
        PsiFile file = element.getContainingFile();

        return (HtmlUtil.hasHtml(file) || HtmlUtil.supportsXmlTypedHandlers(file)) &&
               isAcceptableAttributeValue(element);
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  private static boolean isAcceptableAttributeValue(PsiElement element) {
    final PsiElement parent = element.getParent();

    if (parent instanceof XmlAttribute xmlAttribute) {
      final @NonNls String attrName = xmlAttribute.getName();
      XmlTag tag = xmlAttribute.getParent();
      if (tag == null) return false;

      final @NonNls String tagName = tag.getName();

      return
       ( attrName.equalsIgnoreCase(SRC_ATTR_NAME) &&
         (tagName.equalsIgnoreCase(SizeReference.IMAGE_TAG_NAME) ||
          tagName.equalsIgnoreCase(HtmlUtil.SCRIPT_TAG_NAME) ||
          tagName.equalsIgnoreCase("frame") ||
          tagName.equalsIgnoreCase("iframe") ||
          tagName.equalsIgnoreCase("video") ||
          tagName.equalsIgnoreCase("audio") ||
          tagName.equalsIgnoreCase("track") ||
          tagName.equalsIgnoreCase("source") ||
          tagName.equalsIgnoreCase("embed") ||
          tagName.equalsIgnoreCase("input") &&
          tag.getAttributeValue("type") != null &&
          "image".equalsIgnoreCase(tag.getAttributeValue("type"))
         )
        ) ||
         ( attrName.equalsIgnoreCase(HREF_ATTRIBUTE_NAME) &&
           ( tagName.equalsIgnoreCase("a") ||
          tagName.equalsIgnoreCase("area") ||
          tagName.equalsIgnoreCase("link")
         )
        ) ||
           ( attrName.equalsIgnoreCase(USEMAP_ATTR_NAME) &&
         (tagName.equalsIgnoreCase(SizeReference.IMAGE_TAG_NAME) ||
          tagName.equalsIgnoreCase("object"))
        ) ||
           ( attrName.equalsIgnoreCase("action") &&
         tagName.equalsIgnoreCase("form")
        ) ||
        attrName.equalsIgnoreCase("background") ||
             ( ( attrName.equalsIgnoreCase(NAME_ATTR_LOCAL_NAME) ||
          attrName.equalsIgnoreCase(HtmlUtil.ID_ATTRIBUTE_NAME) ||
          attrName.equalsIgnoreCase(HtmlUtil.CLASS_ATTRIBUTE_NAME)
         ) &&
         tag.getNamespacePrefix().length() == 0
        ) ||
               ( (attrName.equalsIgnoreCase(SizeReference.WIDTH_ATTR_NAME) ||
          attrName.equalsIgnoreCase(SizeReference.HEIGHT_ATTR_NAME)
         ) &&
         (tagName.equalsIgnoreCase(SizeReference.IMAGE_TAG_NAME))
        ) ||
        (attrName.equalsIgnoreCase(ContentTypeReference.TYPE_ATTR_NAME) &&
         (tagName.equalsIgnoreCase(HtmlUtil.STYLE_TAG_NAME) ||
          tagName.equalsIgnoreCase(HtmlUtil.SCRIPT_TAG_NAME)
         )
        ) ||
        (attrName.equalsIgnoreCase(ColorReference.BG_COLOR_ATTR_NAME) &&
         ColorReference.ourBgColorTagNames.contains(StringUtil.toLowerCase(tagName))
        ) ||
                    ( attrName.equalsIgnoreCase(ColorReference.COLOR_ATTR_NAME) &&
         (tagName.equalsIgnoreCase("basefont") ||
          tagName.equalsIgnoreCase("font")
         )
        ) ||
                    ( tagName.equalsIgnoreCase("body") &&
         (attrName.equalsIgnoreCase(ColorReference.TEXT_ATTR_NAME) ||
          attrName.equalsIgnoreCase(ColorReference.LINK_ATTR_NAME) ||
          attrName.equalsIgnoreCase(ColorReference.VLINK_ATTR_NAME) ||
          attrName.equalsIgnoreCase(ColorReference.ALINK_ATTR_NAME)
         )
        ) ||
        (tagName.equalsIgnoreCase("label") &&
         attrName.equalsIgnoreCase(FOR_ATTR_NAME)
        ) ||
        (attrName.equalsIgnoreCase(MicrodataUtil.ITEM_REF) &&
         tag.getAttribute(MicrodataUtil.ITEM_SCOPE) != null
        ) ||
        (attrName.equalsIgnoreCase("data") &&
         tagName.equalsIgnoreCase("object")
                    )||
        (attrName.equalsIgnoreCase("poster") &&
         tagName.equalsIgnoreCase("video")
                    )||
        (attrName.equalsIgnoreCase("srcset") &&
         (tagName.equalsIgnoreCase(SizeReference.IMAGE_TAG_NAME) || tagName.equals("source"))
                    )||
        (attrName.equalsIgnoreCase(LABELLEDBY))
        ;
    }
    return false;
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, final @NotNull ProcessingContext context) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof XmlAttribute attribute)) return PsiReference.EMPTY_ARRAY;
    final String localName = attribute.getLocalName();

    if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) {
      return PsiReference.EMPTY_ARRAY;
    }

    String text = ElementManipulators.getValueText(element);
    int offset = ElementManipulators.getValueTextRange(element).getStartOffset();

    final XmlTag tag = attribute.getParent();
    PsiReference[] refs = PsiReference.EMPTY_ARRAY;
    if (FOR_ATTR_NAME.equalsIgnoreCase(localName) || LABELLEDBY.equals(localName)) {
      refs = new PsiReference[] { new HtmlIdRefReference(element, offset)};
    } else
    if (MicrodataUtil.ITEM_REF.equalsIgnoreCase(localName)) {
      final XmlAttributeValue value = attribute.getValueElement();
      refs = MicrodataUtil.getReferencesForAttributeValue(value, (token, offset1) -> new HtmlIdRefReference(value, offset1));
    } else
    if(ColorReference.BG_COLOR_ATTR_NAME.equalsIgnoreCase(localName) ||
             ColorReference.COLOR_ATTR_NAME.equalsIgnoreCase(localName) ||
             ColorReference.TEXT_ATTR_NAME.equalsIgnoreCase(localName) ||
             ColorReference.LINK_ATTR_NAME.equalsIgnoreCase(localName) ||
             ColorReference.VLINK_ATTR_NAME.equalsIgnoreCase(localName) ||
             ColorReference.ALINK_ATTR_NAME.equalsIgnoreCase(localName)
    ) {
      refs = new PsiReference[] { new ColorReference(element, offset)};
    } else
    if(ContentTypeReference.TYPE_ATTR_NAME.equalsIgnoreCase(localName)) {
      refs = new PsiReference[] { new ContentTypeReference(element)};
    } else if (SizeReference.WIDTH_ATTR_NAME.equalsIgnoreCase(localName) ||
             SizeReference.HEIGHT_ATTR_NAME.equalsIgnoreCase(localName)) {
      refs = new PsiReference[] { new SizeReference(element, offset)};
    } else {
      if (HtmlUtil.ID_ATTRIBUTE_NAME.equalsIgnoreCase(localName)) {
        refs = new PsiReference[] { new HtmlIdSelfReference(element, offset) };
      }
      else if (NAME_ATTR_LOCAL_NAME.equalsIgnoreCase(localName)) {
        refs = new PsiReference[] { new AttributeValueSelfReference(element, offset)};
      }
      else if (HtmlUtil.CLASS_ATTRIBUTE_NAME.equalsIgnoreCase(localName)) {
        List<PsiReference> references = new ArrayList<>(1);
        int ndx = 0;
        for (String token : HtmlUtil.splitClassNames(text)) {
          ndx = text.indexOf(token, ndx);
          references.add(new AttributeValueSelfReference(element, new TextRange(offset + ndx, offset + ndx + token.length())));
          ndx += token.length();
        }
        references.toArray(refs = new PsiReference[references.size()]);
      }
      else if (SRC_ATTR_NAME.equalsIgnoreCase(localName) && SizeReference.IMAGE_TAG_NAME.equalsIgnoreCase(tag.getName())) {
        refs = PathReferenceManager.getInstance().createReferences(element, false, false, true, IMAGE_FILE_TYPES.get());
      }
      else if (("srcset".equalsIgnoreCase(localName) &&
                (SizeReference.IMAGE_TAG_NAME.equalsIgnoreCase(tag.getName()) || "source".equals(tag.getName())) &&
                  PsiTreeUtil.getChildOfType(element, OuterLanguageElement.class) == null))
      {
        final List<PsiReference> result = new ArrayList<>();
        int index = offset;
        for (String imageAndSize : StringUtil.tokenize(text, ",")) {
          int innerIndex = 0;
          while (innerIndex < imageAndSize.length() && Character.isWhitespace(imageAndSize.charAt(innerIndex))) innerIndex++;
          if (innerIndex < imageAndSize.length()) {
            final String image = imageAndSize.substring(innerIndex).split(" ", 2)[0];
            Collections.addAll(result, new FileReferenceSet(image, element, index + innerIndex, null, true, false,
                                                            IMAGE_FILE_TYPES.get()).getAllReferences());
          }
          index += imageAndSize.length() + 1;
        }
        refs = result.toArray(PsiReference.EMPTY_ARRAY);
      } else if (("data".equals(localName) && "object".equalsIgnoreCase(tag.getName())) ||
               ("poster".equals(localName) && "video".equalsIgnoreCase(tag.getName()))) {
        refs = PathReferenceManager.getInstance().createReferences(element, false, false, true);
      }
      else if (HREF_ATTRIBUTE_NAME.equalsIgnoreCase(localName) && "link".equalsIgnoreCase(tag.getName())) {
        if (!HtmlUtil.hasHtmlPrefix(text)) {
          FileType[] fileTypes = findFileTypeByRel(tag.getAttributeValue("rel"));
          String typeValue = tag.getAttributeValue("type");
          fileTypes = fileTypes == null ? findFileType(typeValue == null ? "text/css" : typeValue) : fileTypes;
          if (!text.startsWith("data:")) {
            refs = PathReferenceManager.getInstance().createReferences(element, false, false, true, fileTypes);
          }
        }
      }
      else if (SRC_ATTR_NAME.equalsIgnoreCase(localName) && HtmlUtil.isScriptTag(tag)) {
        if (!HtmlUtil.hasHtmlPrefix(text)) {
          final String typeValue = tag.getAttributeValue("type");
          refs = PathReferenceManager.getInstance().createReferences(element, false, false, true, findFileType(typeValue));
        }
      }
      else if (!StringUtil.startsWithIgnoreCase(text, JAVASCRIPT_PREFIX) &&
               !GlobalPathReferenceProvider.startsWithAllowedPrefix(text)) {
        refs = PathReferenceManager.getInstance().createReferences(element, false, false, true);
      }
    }

    return refs;
  }

  private static FileType[] findFileTypeByRel(@Nullable String rel) {
    if (rel == null) return null;
    for (String type : rel.split(" ")) {
      if ("stylesheet".equalsIgnoreCase(type)) {
        return findFileType("text/css");
      }
      if (type.contains("icon") || type.contains("image")) {
        return IMAGE_FILE_TYPES.get();
      }
    }
    return null;
  }

  private static FileType[] findFileType(@Nullable String mimeType) {
    final Collection<Language> languages = Language.findInstancesByMimeType(mimeType != null ? mimeType.trim() : null);
    FileType fileType = ContainerUtil.find(FileTypeManager.getInstance().getRegisteredFileTypes(),
                                           type -> type instanceof LanguageFileType && languages.contains(((LanguageFileType)type).getLanguage()));
    return fileType == null ? null : new FileType[]{fileType};
  }

  public static String[] getAttributeValues() {
    return new String[] {SRC_ATTR_NAME, HREF_ATTRIBUTE_NAME, USEMAP_ATTR_NAME, "action", "background", "width", "height", "type", "bgcolor", "color", "vlink",
      "link", "alink", "text", "name", HtmlUtil.ID_ATTRIBUTE_NAME, HtmlUtil.CLASS_ATTRIBUTE_NAME, FOR_ATTR_NAME, MicrodataUtil.ITEM_REF, "data", "poster", "srcset",
      LABELLEDBY
    };
  }

  static final class ContentTypeReference extends BasicAttributeValueReference {
    private static final @NonNls String ourStyleContentType = "text/css";

    static final @NonNls String TYPE_ATTR_NAME = "type";

    ContentTypeReference(final PsiElement element) {
      super(element);
    }

    @Override
    public @Nullable PsiElement resolve() {
      return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
      final XmlTag tag = PsiTreeUtil.getParentOfType(myElement, XmlTag.class);

      if (tag != null) {
        if (HtmlUtil.SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getName())) {
          List<String> mimeTypes = new ArrayList<>();
          for (Language language : Language.getRegisteredLanguages()) {
            Collections.addAll(mimeTypes, language.getMimeTypes());
          }
          Collections.sort(mimeTypes);
          mimeTypes.remove("module"); // module is provided by enumerated value
          return mimeTypes.toArray();
        } else if(HtmlUtil.STYLE_TAG_NAME.equalsIgnoreCase(tag.getName())) {
          return new LookupElement[]{
            AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(LookupElementBuilder.create(ourStyleContentType))};
        }
      }

      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }

  public static final class SizeReference extends BasicAttributeValueReference {
    static final @NonNls String WIDTH_ATTR_NAME = "width";
    static final @NonNls String HEIGHT_ATTR_NAME = "height";
    static final @NonNls String IMAGE_TAG_NAME = "img";

    private final boolean myIsWidth;

    public SizeReference(final PsiElement element, int offset) {
      super(element, offset);

      final XmlAttribute xmlAttribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
      assert xmlAttribute != null;
      myIsWidth = WIDTH_ATTR_NAME.equalsIgnoreCase(xmlAttribute.getName());
    }

    @Override
    public @Nullable PsiElement resolve() {
      final ImageInfoReader.Info info = getImageInfo();
      if (info != null && info.width != 0 && info.height != 0) {
        String text = getCanonicalText();
        if (!HtmlUtil.isHtml5Context((XmlElement)myElement) && StringUtil.endsWithChar(text, '%')) {
          return myElement;
        }

        final int myValue = getSizeValue(text, ((XmlElement)myElement));
        if (myIsWidth && info.width != myValue ||
            !myIsWidth && info.height != myValue) {
          return null;
        }
      }
      return myElement;
    }

    static int getSizeValue(@Nullable String value, @NotNull XmlElement context) {
      if (value == null) {
        return 0;
      }
      if (!HtmlUtil.isHtml5Context(context)) {
        value = StringUtil.trimEnd(value, "px").trim();
      }
      return StringUtil.parseInt(value, 0);
    }

    @Nullable
    ImageInfoReader.Info getImageInfo() {
      final XmlTag tag = PsiTreeUtil.getNonStrictParentOfType(myElement, XmlTag.class);
      return tag != null ? getImageInfo(tag) : null;
    }

    public static @Nullable ImageInfoReader.Info getImageInfo(final @NotNull XmlTag tag) {
      return CachedValuesManager.getCachedValue(tag, () -> {
        PsiElement srcValue = JBIterable.from(HtmlAttributeValueProvider.EP_NAME.getExtensionList())
          .filterMap(it -> it.getCustomAttributeValue(tag, SRC_ATTR_NAME))
          .first();
        if (srcValue == null) {
          var attr = tag.getAttribute(SRC_ATTR_NAME, null);
          srcValue = attr != null ? attr.getValueElement() : null;
        }
        if (srcValue != null) {
          final PsiFile psiFile = FileReferenceUtil.findFile(srcValue);
          if (psiFile != null) {
            final VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile instanceof VirtualFileWithId) {
              ImageInfo value = ImageInfoIndex.getInfo(virtualFile, tag.getProject());
              if (value == null) return null;
              return CachedValueProvider.Result.create(
                new ImageInfoReader.Info(value.width, value.height, value.bpp, IfsUtil.isSVG(virtualFile)),
                srcValue,
                virtualFile
              );
            }
          }
          final String srcValueText = doIfNotNull(srcValue.getText(), StringUtil::unquoteString);
          if (srcValueText != null && URLUtil.isDataUri(srcValueText)) {
            final byte[] bytesFromDataUri = URLUtil.getBytesFromDataUri(srcValueText);
            if (bytesFromDataUri != null) {
              try {
                ImageInfoReader.Info info = ImageInfoReader.getInfo(bytesFromDataUri);
                if (info != null) {
                  return CachedValueProvider.Result.create(info, srcValue);
                }
              }
              catch (Exception ignored) {
              }
            }
          }
        }
        return CachedValueProvider.Result.create(null, tag);
      });
    }

    @Override
    public Object @NotNull [] getVariants() {
      final ImageInfoReader.Info info = getImageInfo();
      if (myIsWidth && info != null && info.width != 0) {
        return new Object[]{
          AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(LookupElementBuilder.create(String.valueOf(info.width)))
        };
      }
      if (!myIsWidth && info != null && info.height != 0) {
        return new Object[]{
          AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(LookupElementBuilder.create(String.valueOf(info.height)))
        };
      }
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }

  private static final class HtmlIdSelfReference extends AttributeValueSelfReference {
    HtmlIdSelfReference(final PsiElement element, int offset) {
      super(element, offset);
    }

    @Override
    public Object @NotNull [] getVariants() {
      final List<String> result = new LinkedList<>();

      IdRefReference.process(new PsiElementProcessor<>() {
        @Override
        public boolean execute(final @NotNull PsiElement element) {
          if (element instanceof XmlTag) {
            String forValue = ((XmlTag)element).getAttributeValue(IdReferenceProvider.FOR_ATTR_NAME);
            if (forValue != null) {
              result.add(forValue);
            }
          }
          return true;
        }
      }, myElement.getContainingFile());

      return ArrayUtil.toObjectArray(result);
    }
  }

  public static final class HtmlIdRefReference extends IdRefReference {
    public HtmlIdRefReference(PsiElement element, int offset) {
      super(element, offset, true);
    }

    @Override
    public boolean isSoft() {
      return true;
    }
  }
}
