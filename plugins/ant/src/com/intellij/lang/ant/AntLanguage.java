package com.intellij.lang.ant;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.ant.validation.AntAnnotator;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntLanguage extends Language {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.AntLanguage");
  private final Language myXmlLanguage;
  private AntParserDefinition myParserDefinition;
  private AntAnnotator myAnnotator;
  private NamesValidator myNamesValidator;

  public AntLanguage() {
    super("ANT", "text/xml");
    myXmlLanguage = StdLanguages.XML;
    LOG.assertTrue(myXmlLanguage != null, "AntLanguage should be created after XmlLanguage has created.");
  }

  @Nullable
  public ParserDefinition getParserDefinition() {
    if (myParserDefinition == null) {
      myParserDefinition = new AntParserDefinition(myXmlLanguage.getParserDefinition());
    }
    return myParserDefinition;
  }

  @Nullable
  public Annotator getAnnotator() {
    if (myAnnotator == null) {
      myAnnotator = new AntAnnotator();
    }
    return myAnnotator;
  }

  @NotNull
  public NamesValidator getNamesValidator() {
    if (myNamesValidator == null) {
      myNamesValidator = new NamesValidator() {
        public boolean isKeyword(String name, Project project) {
          return false;
        }
        public boolean isIdentifier(String name, Project project) {
          return true;
        }
      };
    }
    return myNamesValidator;
  }

  @Nullable
  public Commenter getCommenter() {
    return myXmlLanguage.getCommenter();
  }
}
