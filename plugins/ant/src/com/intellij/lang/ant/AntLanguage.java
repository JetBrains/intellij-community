package com.intellij.lang.ant;

import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

public class AntLanguage extends Language {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.AntLanguage");
  private final Language myXmlLanguage;
  private AntParserDefinition myXmlParserDefinition;

  public AntLanguage() {
    super("ANT", "text/xml");
    myXmlLanguage = StdLanguages.XML;
    LOG.assertTrue(myXmlLanguage != null, "AntLanguage should be created after XmlLanguage has created.");
  }

  @Nullable
  public ParserDefinition getParserDefinition() {
    if (myXmlParserDefinition == null) {
      myXmlParserDefinition = new AntParserDefinition(myXmlLanguage.getParserDefinition());
    }
    return myXmlParserDefinition;
  }
}
