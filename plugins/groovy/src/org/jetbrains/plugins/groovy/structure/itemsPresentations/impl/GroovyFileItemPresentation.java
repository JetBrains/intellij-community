package org.jetbrains.plugins.groovy.structure.itemsPresentations.impl;

import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.structure.GroovyElementPresentation;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;

/**
 * User: Dmitry.Krasilschikov
  * Date: 30.10.2007
  */
 public class GroovyFileItemPresentation extends GroovyItemPresentation {
  public GroovyFileItemPresentation(GroovyFileBase myElement) {
    super(myElement);
  }

  public String getPresentableText() {
    return GroovyElementPresentation.getFilePresentableText(((GroovyFileBase) myElement));
  }
}
