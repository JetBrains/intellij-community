/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import com.intellij.application.options.CodeStyle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsIgnore.IgnoreFileFilter;
import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.javacvsImpl.io.SendTextFilePreprocessor;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.file.ILocalFileReader;
import org.netbeans.lib.cvsclient.file.LocalFileReader;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class CheckoutProjectOperation extends CvsCommandOperation {
  private final String[] myModuleNames;
  private final CvsEnvironment myEnvironment;
  private final boolean myMakeNewFilesReadOnly;

  private final File myRoot;
  private final String myAlternateCheckoutDirectory;
  private final boolean myPruneEmptyDirectories;
  private final KeywordSubstitution myKeywordSubstitution;

  public static CheckoutProjectOperation createTestInstance(CvsEnvironment env, String moduleName, File targetLocation) {
    return create(env, new String[]{moduleName}, targetLocation, false, false);
  }

  public CheckoutProjectOperation(String[] moduleNames,
                                 CvsEnvironment environment,
                                 boolean makeNewFilesReadOnly,
                                 File root,
                                 String alternateCheckoutDirectory,
                                 boolean pruneEmptyDirectories,
                                 KeywordSubstitution keywordSubstitution) {
    super(new CheckoutAdminReader(),
      new CheckoutAdminWriter(CodeStyle.getDefaultSettings().getLineSeparator(),
        CvsApplicationLevelConfiguration.getCharset()));
    myModuleNames = moduleNames;
    myEnvironment = environment;
    myMakeNewFilesReadOnly = makeNewFilesReadOnly;
    myRoot = root;
    myAlternateCheckoutDirectory = alternateCheckoutDirectory;
    myPruneEmptyDirectories = pruneEmptyDirectories;
    myKeywordSubstitution = keywordSubstitution;
  }

  public static CheckoutProjectOperation create(CvsEnvironment env,
                                                String[] moduleName,
                                                File targetLocation,
                                                boolean useAlternativeCheckoutDir,
                                                boolean makeNewFilesReadOnly) {
    final CvsApplicationLevelConfiguration config = CvsApplicationLevelConfiguration.getInstance();
    final KeywordSubstitutionWrapper substitution = KeywordSubstitutionWrapper.getValue(config.CHECKOUT_KEYWORD_SUBSTITUTION);

    final File root;
    final String directory;
    if (useAlternativeCheckoutDir && targetLocation.getParentFile() == null) {
      root = targetLocation;
      directory = getModuleRootName(moduleName);
    }
    else if (useAlternativeCheckoutDir) {
      root = targetLocation.getParentFile();
      directory = targetLocation.getName();
    }
    else {
      root = targetLocation;
      directory = null;
    }

    return new CheckoutProjectOperation(moduleName,
                                        env,
                                        makeNewFilesReadOnly,
                                        root,
                                        directory,
                                        config.CHECKOUT_PRUNE_EMPTY_DIRECTORIES,
                                        substitution == null ? null : substitution.getSubstitution());
  }

  private static String getModuleRootName(String[] moduleNames) {
    File current = new File(moduleNames[0]);
    while (current.getParentFile() != null) current = current.getParentFile();
    return current.getName();
  }

  protected ILocalFileReader createLocalFileReader() {
    return new LocalFileReader(new SendTextFilePreprocessor());
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final CheckoutCommand command = new CheckoutCommand(() -> ((CheckoutAdminWriter) myAdminWriter).finish());
    command.setRecursive(true);
    for (String myModuleName : myModuleNames) {
      command.addModule(myModuleName);
    }
    root.getRevisionOrDate().setForCommand(command);
    command.setAlternativeCheckoutDirectory(myAlternateCheckoutDirectory);
    command.setPruneDirectories(myPruneEmptyDirectories);
    command.setKeywordSubstitution(myKeywordSubstitution);
    return command;
  }

  protected Collection<CvsRootProvider> getAllCvsRoots() {
    return Collections.singleton(CvsRootProvider.createOn(getRoot(), myEnvironment));
  }

  private File getRoot() {
    return myRoot;
  }

  protected IIgnoreFileFilter getIgnoreFileFilter() {
    return new IgnoreFileFilter();
  }

  protected String getOperationName() {
    return "checkout";
  }

  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setCheckedOutFilesReadOnly(myMakeNewFilesReadOnly);
  }

  @Override
  public boolean runInReadThread() {
    return false;
  }
}
