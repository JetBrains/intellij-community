package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentProvider;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.checkout.ListModulesCommand;
import org.netbeans.lib.cvsclient.command.checkout.Module;

import java.util.Collection;
import java.util.Iterator;

public class GetModulesListOperation extends LocalPathIndifferentOperation implements DirectoryContentProvider{
  private final ListModulesCommand myCommand = new ListModulesCommand();


  public GetModulesListOperation(CvsEnvironment environment) {
    super(environment);
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    return myCommand;
  }

  public Collection getModulesInRepository() {
    return myCommand.getModules();
  }

  protected String getOperationName() {
    return "checkout";
  }

  public DirectoryContent getDirectoryContent() {
    DirectoryContent result = new DirectoryContent();
    Collection modules = myCommand.getModules();
    for (Iterator iterator = modules.iterator(); iterator.hasNext();) {
      Module module = (Module) iterator.next();
      result.addModule(module.getModuleName());
    }
    return result;
  }
}
