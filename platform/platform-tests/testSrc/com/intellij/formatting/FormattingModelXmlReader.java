package com.intellij.formatting;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@NonNls public class FormattingModelXmlReader {

  private final Map<String, Alignment> myIdToAlignemt = new HashMap<>();
  private final Map<String, Wrap> myIdToWrap = new HashMap<>();
  private final FormattingDocumentModel myModel;

  public FormattingModelXmlReader(final FormattingDocumentModel model) {
    myModel = model;
  }

  public TestBlock readTestBlock(String dataName) throws IOException, JDOMException {
    final File dataFile = new File( PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') +
                                    "/platform/platform-tests/testData/newGeneralFormatter", dataName + ".xml");
    return readBlock(JDOMUtil.load(dataFile));
  }
  
  public TestBlock readTestBlock(String path, String file) throws IOException, JDOMException {
    final File dataFile = new File(path, file);
    return readBlock(JDOMUtil.load(dataFile));
  }

  private TestBlock readBlock(final Element rootElement) {
    final int startOffset = Integer.parseInt(rootElement.getAttributeValue("start"));
    final int endOffset = Integer.parseInt(rootElement.getAttributeValue("end"));
    final TextRange textRange = new TextRange(startOffset, endOffset);
    final TestBlock result = new TestBlock(textRange);
    result.setIsIncomplete(Boolean.valueOf(rootElement.getAttributeValue("incomplete")).booleanValue());
    final CharSequence text = myModel.getText(textRange);
    if (text != null) {
      result.setText(text.toString());
    }
    final Element indentElement = rootElement.getChild("Indent");
    if (indentElement != null) {
      result.setIndent(readIndent(indentElement));
    }
    final Element alignmentElement = rootElement.getChild("Alignment");
    if (alignmentElement != null) {
      result.setAlignment(readAlignment(alignmentElement));
    }

    final Element wrapElement = rootElement.getChild("Wrap");
    if (wrapElement != null) {
      result.setWrap(readWrap(wrapElement));
    }
    final List children = rootElement.getChildren();
    for (final Object aChildren : children) {
      Element element = (Element)aChildren;
      if (element.getName().equals("Space")) {
        result.addSpace(readSpace(element));
      }
      else if (element.getName().equals("Block")) {
        result.addBlock(readBlock(element));
      }
    }
    return result;
  }

  private Wrap readWrap(final Element wrapElement) {
    final String wrapId = wrapElement.getAttributeValue("id");
    if (myIdToWrap.containsKey(wrapId)) return myIdToWrap.get(wrapId);

    final String type = wrapElement.getAttributeValue("type");
    String parentId = wrapElement.getAttributeValue("parent");
    boolean wrapFirst = "true".equals(wrapElement.getAttributeValue("ignoreParents"));

    if (myIdToWrap.containsKey(parentId)) {
      final Wrap wrap = Wrap.createChildWrap(myIdToWrap.get(parentId),readWrapType(type), wrapFirst);
      if ("true".equals(wrapElement.getAttributeValue("ignoreParents"))) {
        wrap.ignoreParentWraps();
      }
      myIdToWrap.put(wrapId,  wrap);
      return wrap;
    } else {
      final Wrap wrap = Wrap.createWrap(readWrapType(type), wrapFirst);
      if ("true".equals(wrapElement.getAttributeValue("ignoreParents"))) {
        wrap.ignoreParentWraps();
      }
      myIdToWrap.put(wrapId,  wrap);
      return wrap;
    }

  }

  private WrapType readWrapType(final String type) {
    if ("ALWAYS".equals(type)) return WrapType.ALWAYS;
    if ("NORMAL".equals(type)) return WrapType.NORMAL;
    if ("CHOP".equals(type)) return WrapType.CHOP_DOWN_IF_LONG;
    return WrapType.NONE;
  }

  private Spacing readSpace(final Element element) {
    return Spacing.createSpacing(
      getInt(element.getAttributeValue("minspaces")),
      getInt(element.getAttributeValue("maxspaces")),
      getInt(element.getAttributeValue("minlinefeeds")),
      "true".equals(element.getAttributeValue("keepLineBreaks")), 0);
  }

  private Alignment readAlignment(final Element alignmentElement) {
    final String alignId = alignmentElement.getAttributeValue("id");
    if (myIdToAlignemt.containsKey(alignId)) return myIdToAlignemt.get(alignId);

    final Alignment alignment = Alignment.createAlignment();
    myIdToAlignemt.put(alignId,  alignment);
    return alignment;
  }

  private Indent readIndent(final Element indentElement) {
    final String type = indentElement.getAttributeValue("type");
    if ("LABEL".equals(type)) return Indent.getLabelIndent();
    if ("NONE".equals(type)) return Indent.getNoneIndent();
    if ("CONTINUATION".equals(type)) return Indent.getContinuationIndent();
    if ("SPACE".equals(type)) {
      String spaces = indentElement.getAttributeValue("spaces");
      return Indent.getSpaceIndent(Integer.valueOf(spaces));
    }
    return Indent.getNormalIndent();
  }

  private int getInt(final String count) {
    try {
      return Integer.parseInt(count);
    }
    catch (Exception e) {
      return 0;
    }
  }
}
