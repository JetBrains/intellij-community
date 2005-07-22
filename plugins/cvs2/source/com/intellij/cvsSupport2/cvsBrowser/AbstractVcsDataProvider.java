package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentProvider;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetDirectoriesListViaUpdateOperation;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public abstract class AbstractVcsDataProvider implements RemoteResourceDataProvider {

  protected final CvsEnvironment myEnvironment;
  protected final boolean myShowFiles;
  protected final boolean myShowModules;

  public AbstractVcsDataProvider(CvsEnvironment environment,
                                 boolean showFiles,
                                 boolean showModules) {
    myEnvironment = environment;
    myShowFiles = showFiles;
    myShowModules = showModules;
  }

  public void fillContentFor(CvsElement element, Project project, GetContentCallback callback) {
    collectTreeElementsTo(element, project, callback);
  }

  protected void collectTreeElementsTo(CvsElement parent,
                                       Project project,
                                       GetContentCallback callback) {
    fillDirectoryContent(parent, parent.getElementPath(), callback, project);
  }


  public void fillDirectoryContent(final CvsElement parent,
                                   String path,
                                   final GetContentCallback callback,
                                   final Project project) {

    executeCommand(createDirectoryContentProvider(path), callback, parent, project);

  }

  public DirectoryContentProvider createDirectoryContentProvider(String path) {
    return new GetDirectoriesListViaUpdateOperation(myEnvironment, path);
  }

  protected void executeCommand(final DirectoryContentProvider command,
                                final GetContentCallback callback,
                                final CvsElement parent,
                                final Project project) {
    final CvsOperationExecutor executor1 = new CvsOperationExecutor(false, project,
                                                                    ModalityState.stateForComponent(
                                                                      parent.getTree()));
    executor1.setIsQuietOperation(true);

    executor1.performActionSync(
      new CommandCvsHandler("Browse Repository", (CvsOperation)command, false){
        protected boolean runInReadThread() {
          return false;
        }
      },
      new CvsOperationExecutorCallback() {
        public void executionFinished(boolean successfully) {
          callback.finished();
          if (!executor1.isLoggedIn()) {
            callback.loginAborted();
          }
          else {
            ArrayList children = new ArrayList();
            DirectoryContent directoryContent = command.getDirectoryContent();
            if (myShowModules) {
              children.addAll(addModules(directoryContent, parent, project));
            }
            children.addAll(addSubDirectories(directoryContent, parent, project));
            if (myShowFiles) {
              children.addAll(addFiles(directoryContent, parent, project));
            }
            callback.fillDirectoryContent(children);
          }
        }

        public void executeInProgressAfterAction(ModalityContext modaityContext) {
        }

        public void executionFinishedSuccessfully() {
        }
      });
  }

  private ArrayList addFiles(DirectoryContent children, CvsElement element, Project project) {
    return createCvsNodesOn(children.getFiles(), element,
                            CvsElementFactory.FILE_ELEMENT_FACTORY,
                            RemoteResourceDataProvider.NOT_EXPANDABLE, project);
  }

  private ArrayList addSubDirectories(DirectoryContent children,
                                      CvsElement element,
                                      Project project) {
    return createCvsNodesOn(children.getSubDirectories(), element,
                            CvsElementFactory.FOLDER_ELEMENT_FACTORY, getChildrenDataProvider(),
                            project);
  }

  private ArrayList addModules(DirectoryContent children, CvsElement element, Project project) {
    Collection modules = children.getSubModules();
    return createCvsNodesOn(modules, element,
                            CvsElementFactory.MODULE_ELEMENT_FACTORY,
                            new ModuleDataProvider(myEnvironment, myShowFiles),
                            project);
  }

  protected ArrayList createCvsNodesOn(Collection children,
                                       CvsElement element,
                                       CvsElementFactory elementFactory,
                                       RemoteResourceDataProvider dataProvider,
                                       Project project) {
    ArrayList result = new ArrayList();
    for (Iterator each = children.iterator(); each.hasNext();) {
      String name = (String)each.next();
      result.add(
        setElementDetails(elementFactory.createElement(name, myEnvironment, project), name,
                          element, dataProvider));
    }
    return result;
  }

  private CvsElement setElementDetails(CvsElement result, String name,
                                       CvsElement parent, RemoteResourceDataProvider dataProvider) {
    result.setDataProvider(dataProvider);
    result.setName(name);
    result.setPath(getPathForName(name, parent));
    result.setModel(parent.getModel());
    return result;
  }

  protected abstract AbstractVcsDataProvider getChildrenDataProvider();

  protected String getPathForName(String name, CvsElement parent) {
    return parent.createPathForChild(name);
  }
}
