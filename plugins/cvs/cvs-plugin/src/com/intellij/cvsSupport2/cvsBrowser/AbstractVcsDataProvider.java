/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentProvider;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetDirectoriesListViaUpdateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractVcsDataProvider implements RemoteResourceDataProvider {
  protected final CvsEnvironment myEnvironment;
  protected final boolean myShowFiles;
  protected final boolean myShowModules;

  protected AbstractVcsDataProvider(CvsEnvironment environment,
                                 boolean showFiles,
                                 boolean showModules) {
    myEnvironment = environment;
    myShowFiles = showFiles;
    myShowModules = showModules;
  }

  public void fillContentFor(CvsElement element, Project project, GetContentCallback callback) {
    collectTreeElementsTo(element, project, callback);
  }

  private void collectTreeElementsTo(CvsElement parent,
                                       Project project,
                                       GetContentCallback callback) {
    fillDirectoryContent(parent, parent.getElementPath(), callback, project);
  }


  private void fillDirectoryContent(final CvsElement parent,
                                   String path,
                                   final GetContentCallback callback,
                                   final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      executeCommand(createDirectoryContentProvider(path), callback, parent, project);
    } else {
      final GetDirectoriesListViaUpdateOperation provider = new GetDirectoriesListViaUpdateOperation(myEnvironment, path);
      final MyBufferedConsumer consumer = new MyBufferedConsumer(new Consumer<DirectoryContent>() {
        public void consume(final DirectoryContent directoryContent) {
          final ArrayList<CvsElement> elements = directoryContentToElements(directoryContent, parent, project);
          if (!elements.isEmpty()) {
            callback.appendDirectoryContent(elements);
          }
        }
      });
      provider.setStepByStepListener(consumer);
      executeCommand(provider, callback, parent, project);
      consumer.flush();
    }
  }

  private static class MyBufferedConsumer implements Consumer<DirectoryContent> {
    private static final int ourSize = 15;
    private final DirectoryContent myBuffer;
    private final Consumer<DirectoryContent> myDelegate;

    private MyBufferedConsumer(final Consumer<DirectoryContent> delegate) {
      myDelegate = delegate;
      myBuffer = new DirectoryContent();
    }

    public void consume(final DirectoryContent directoryContent) {
      myBuffer.copyDataFrom(directoryContent);
      if (myBuffer.getTotalSize() >= ourSize) {
        myDelegate.consume(myBuffer);
        myBuffer.clear();
      }
    }

    public void flush() {
      if (myBuffer.getTotalSize() > 0) {
        myDelegate.consume(myBuffer);
      }
    }
  }

  public DirectoryContentProvider createDirectoryContentProvider(String path) {
    return new GetDirectoriesListViaUpdateOperation(myEnvironment, path);
  }

  private static class MyCancellableCvsHandler extends CommandCvsHandler {
    private MyCancellableCvsHandler(final String title, final CvsOperation cvsOperation) {
      super(title, cvsOperation, true);
    }

    protected boolean runInReadThread() {
      return false;
    }

    // in order to allow progress listener retrieval
    @Override
    public CvsListenerWithProgress getProgressListener() {
      return super.getProgressListener();
    }
  }

  private void executeCommand(final DirectoryContentProvider command,
                                final GetContentCallback callback,
                                final CvsElement parent,
                                final Project project) {
    final CvsOperationExecutor executor1 = new CvsOperationExecutor(false, project,
                                                                    ModalityState.stateForComponent(
                                                                      parent.getTree()));
    executor1.setIsQuietOperation(true);

    final MyCancellableCvsHandler cvsHandler =
        new MyCancellableCvsHandler(CvsBundle.message("browse.repository.operation.name"), (CvsOperation) command);

    callback.useForCancel(cvsHandler.getProgressListener());

    executor1.performActionSync(cvsHandler,
      new CvsOperationExecutorCallback() {
        public void executionFinished(boolean successfully) {
          callback.finished();
          DirectoryContent directoryContent = command.getDirectoryContent();
          ArrayList<CvsElement> children = directoryContentToElements(directoryContent, parent, project);
          callback.fillDirectoryContent(children);
        }

        public void executeInProgressAfterAction(ModalityContext modaityContext) {
        }

        public void executionFinishedSuccessfully() {
        }
      });
  }

  private ArrayList<CvsElement> directoryContentToElements(final DirectoryContent directoryContent, final CvsElement parent, final Project project) {
    ArrayList<CvsElement> children = new ArrayList<CvsElement>();
    if (myShowModules) {
      children.addAll(addModules(directoryContent, parent, project));
    }
    children.addAll(addSubDirectories(directoryContent, parent, project));
    if (myShowFiles) {
      children.addAll(addFiles(directoryContent, parent, project));
    }
    return children;
  }

  private ArrayList<CvsElement> addFiles(DirectoryContent children, CvsElement element, Project project) {
    return createCvsNodesOn(children.getFiles(), element,
                            CvsElementFactory.FILE_ELEMENT_FACTORY,
                            RemoteResourceDataProvider.NOT_EXPANDABLE, project);
  }

  private ArrayList<CvsElement> addSubDirectories(DirectoryContent children,
                                                  CvsElement element,
                                                  Project project) {
    return createCvsNodesOn(children.getSubDirectories(), element,
                            CvsElementFactory.FOLDER_ELEMENT_FACTORY, getChildrenDataProvider(),
                            project);
  }

  private ArrayList<CvsElement> addModules(DirectoryContent children, CvsElement element, Project project) {
    Collection<String> modules = children.getSubModules();
    return createCvsNodesOn(modules, element,
                            CvsElementFactory.MODULE_ELEMENT_FACTORY,
                            new ModuleDataProvider(myEnvironment, myShowFiles),
                            project);
  }

  protected ArrayList<CvsElement> createCvsNodesOn(Collection<String> children,
                                                   CvsElement element,
                                                   CvsElementFactory elementFactory,
                                                   RemoteResourceDataProvider dataProvider,
                                                   Project project) {
    ArrayList<CvsElement> result = new ArrayList<CvsElement>();
    for (final String name: children) {
      result.add(setElementDetails(elementFactory.createElement(name, myEnvironment, project), name, element, dataProvider));
    }
    return result;
  }

  private static CvsElement setElementDetails(CvsElement result, String name,
                                              CvsElement parent, RemoteResourceDataProvider dataProvider) {
    result.setDataProvider(dataProvider);
    result.setName(name);
    result.setPath(getPathForName(name, parent));
    result.setModel(parent.getModel());
    return result;
  }

  protected abstract AbstractVcsDataProvider getChildrenDataProvider();

  private static String getPathForName(String name, CvsElement parent) {
    return parent.createPathForChild(name);
  }
}
