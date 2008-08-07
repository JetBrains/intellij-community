package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;

/**
 * @author ilyas
 */
public class GrFileStubImpl extends PsiFileStubImpl<GroovyFile> implements GrFileStub {
  private final StringRef myPackageName;
  private final StringRef myName;
  private final boolean isScript;

  public GrFileStubImpl(GroovyFile file) {
    super(file);
    myPackageName = StringRef.fromString(file.getPackageName());
    myName = StringRef.fromString(StringUtil.trimEnd(file.getName(), ".groovy"));
    isScript = file.isScript();
  }

  public GrFileStubImpl(StringRef packName, StringRef name, boolean isScript) {
    super(null);
    myPackageName = packName;
    myName = name;
    this.isScript = isScript;
  }

  public IStubFileElementType getType() {
    return GroovyElementTypes.GROOVY_FILE;
  }

  public StringRef getPackageName() {
    return myPackageName;
  }

  public StringRef getName() {
    return myName;
  }

  public boolean isScript() {
    return isScript;
  }
}
