package com.intellij.cvsSupport2.cvsoperations.cvsContent;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.*;
import com.intellij.cvsSupport2.cvsoperations.common.*;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentListener;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentProvider;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.cvsSupport2.javacvsImpl.io.DeafLocalFileWriter;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.file.ILocalFileReader;
import org.netbeans.lib.cvsclient.file.ILocalFileWriter;

/**
 * author: lesya
 */
public class GetModuleContentOperation extends CompositeOperaton implements DirectoryContentProvider {
  private final DirectoryContentListener myDirectoryContentListener = new DirectoryContentListener();
  private final AdminWriterStoringRepositoryPath myAdminWriterStoringRepositoryPath;
  private String myModuleLocation;

  public GetModuleContentOperation(CvsEnvironment environment, final String moduleName) {
    myAdminWriterStoringRepositoryPath = new AdminWriterStoringRepositoryPath(moduleName, environment.getCvsRootAsString());
    addOperation(createExpandingRepositoryPathOperation(myAdminWriterStoringRepositoryPath, environment, moduleName));
    addOperation(createGetModuleContentOperation(myAdminWriterStoringRepositoryPath, environment, moduleName));
  }

  private LocalPathIndifferentOperation createGetModuleContentOperation(RepositoryPathProvider adminWriter,
                                                                        CvsEnvironment environment,
                                                                        final String moduleName) {
    return new LocalPathIndifferentOperation(new AdminReaderOnStoredRepositoryPath(adminWriter), environment) {
      private boolean myIsInModule = false;

      protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
        CheckoutCommand result = new CheckoutCommand();
        result.addModule(moduleName);
        result.setRecursive(true);
        return result;
      }

      protected ILocalFileReader createLocalFileReader() {
        return ConstantLocalFileReader.FOR_EXISTING_FILE;
      }

      protected String getOperationName() {
        return "checkout";
      }

      protected ILocalFileWriter createLocalFileWriter(String cvsRoot,
                                                       UpdatedFilesManager mergedFilesCollector,
                                                       CvsExecutionEnvironment cvsExecutionEnvironment) {
        return DeafLocalFileWriter.INSTANCE;
      }

      public void messageSent(String message, boolean error, boolean tagged) {
        super.messageSent(message, error, tagged);
        myDirectoryContentListener.setModulePath(myAdminWriterStoringRepositoryPath.getModulePath());
        if (message.startsWith("cvs server: Updating ")) {
          if ((myModuleLocation != null) && message.equals("cvs server: Updating " + myModuleLocation)) {
            myIsInModule = true;
          }
          else {
            myIsInModule = false;
          }
        }
        else if (DirectoryContentListener.moduleMessage(message)) {
          myIsInModule = true;
        }

        if (myIsInModule) {
          myDirectoryContentListener.messageSent(message);
        }
      }

      public void modifyOptions(GlobalOptions options) {
        super.modifyOptions(options);
        options.setDoNoChanges(true);
      }
    };
  }

  private LocalPathIndifferentOperation createExpandingRepositoryPathOperation(
    AdminWriterStoringRepositoryPath adminWriter, CvsEnvironment environment, final String moduleName) {
    return new LocalPathIndifferentOperation(adminWriter, environment) {
      protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
        CheckoutCommand result = new CheckoutCommand();
        result.addModule(moduleName);
        result.setRecursive(false);
        return result;
      }

      protected ILocalFileWriter createLocalFileWriter(String cvsRoot,
                                                       UpdatedFilesManager mergedFilesCollector,
                                                       CvsExecutionEnvironment cvsExecutionEnvironment) {
        return DeafLocalFileWriter.INSTANCE;
      }

      protected String getOperationName() {
        return "checkout";
      }

      public void moduleExpanded(String module) {
        super.moduleExpanded(module);
        if (myModuleLocation == null) myModuleLocation = module;
      }
    };
  }

  public DirectoryContent getDirectoryContent() {
    return myDirectoryContentListener.getDirectoryContent();
  }
}
