package org.jetbrains.plugins.groovy.dsl;

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class GroovyScriptDescriptor extends GroovyClassDescriptor implements ScriptDescriptor {
  @NotNull private final GroovyFile myFile;

  public GroovyScriptDescriptor(GroovyFile file, GroovyScriptClass scriptClass) {
    super(scriptClass);
    myFile = file;
  }

  @Nullable
  public String getExtension() {
    return myFile.getViewProvider().getVirtualFile().getExtension();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GroovyScriptDescriptor that = (GroovyScriptDescriptor)o;

    if (!myFile.equals(that.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myFile.hashCode();
    return result;
  }
}
