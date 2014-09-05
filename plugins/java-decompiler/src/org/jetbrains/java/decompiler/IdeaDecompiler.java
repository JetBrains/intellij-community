/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

public class IdeaDecompiler extends ClassFileDecompilers.Light {
  private static final Logger LOG = Logger.getInstance(IdeaDecompiler.class);

  private static final String BANNER =
    "//\n" +
    "// Source code recreated from a .class file by IntelliJ IDEA\n" +
    "// (powered by Fernflower decompiler)\n" +
    "//\n\n";

  private final IFernflowerLogger myLogger = new IdeaLogger();
  private final HashMap<String, Object> myOptions = new HashMap<String, Object>();

  public IdeaDecompiler() {
    myOptions.put(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR, "0");
    myOptions.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
    myOptions.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
    myOptions.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
    myOptions.put(IFernflowerPreferences.LITERALS_AS_IS, "1");
    myOptions.put(IFernflowerPreferences.NEW_LINE_SEPARATOR, "1");

    Project project = DefaultProjectFactory.getInstance().getDefaultProject();
    CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    myOptions.put(IFernflowerPreferences.INDENT_STRING, StringUtil.repeat(" ", options.INDENT_SIZE));
  }

  @Override
  public boolean accepts(@NotNull VirtualFile file) {
    return true;
  }

  @NotNull
  @Override
  public CharSequence getText(@NotNull VirtualFile file) {
    if (!canHandle(file)) {
      return ClsFileImpl.decompile(file);
    }

    try {
      Map<String, VirtualFile> files = ContainerUtil.newLinkedHashMap();
      files.put(file.getPath(), file);
      String mask = file.getNameWithoutExtension() + "$";
      for (VirtualFile child : file.getParent().getChildren()) {
        if (child.getNameWithoutExtension().startsWith(mask) && file.getFileType() == StdFileTypes.CLASS) {
          files.put(child.getPath(), child);
        }
      }
      MyBytecodeProvider provider = new MyBytecodeProvider(files);
      MyResultSaver saver = new MyResultSaver();

      BaseDecompiler decompiler = new BaseDecompiler(provider, saver, myOptions, myLogger);
      for (String path : files.keySet()) {
        decompiler.addSpace(new File(path), true);
      }
      decompiler.decompileContext();

      return BANNER + saver.myResult;
    }
    catch (Exception e) {
      LOG.error(file.getUrl(), e);
      return ClsFileImpl.decompile(file);
    }
  }

  private static boolean canHandle(VirtualFile file) {
    if ("package-info.class".equals(file.getName())) {
      LOG.info("skipped: " + file.getUrl());
      return false;
    }

    final Ref<Boolean> isGroovy = Ref.create(false);
    try {
      new ClassReader(file.contentsToByteArray()).accept(new ClassVisitor(Opcodes.ASM5) {
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
          for (String anInterface : interfaces) {
            if ("groovy/lang/GroovyObject".equals(anInterface)) {
              isGroovy.set(true);
              break;
            }
          }
        }

        @Override
        public void visitSource(String source, String debug) {
          if (source != null && source.endsWith(".groovy")) {
            isGroovy.set(true);
          }
        }
      }, ClassReader.SKIP_CODE);
    }
    catch (Exception e) {
      throw new RuntimeException("corrupted file: " + file.getUrl(), e);
    }
    if (isGroovy.get()) {
      LOG.info("skipped Groovy class: " + file.getUrl());
      return false;
    }

    return true;
  }

  private static class MyBytecodeProvider implements IBytecodeProvider {
    private final Map<String, VirtualFile> myFiles;

    private MyBytecodeProvider(@NotNull Map<String, VirtualFile> files) {
      myFiles = files;
    }

    @Override
    public byte[] getBytecode(String externalPath, String internalPath) {
      try {
        String path = FileUtil.toSystemIndependentName(externalPath);
        VirtualFile file = myFiles.get(path);
        assert file != null : path;
        return file.contentsToByteArray();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class MyResultSaver implements IResultSaver {
    private String myResult = "";

    @Override
    public void saveClassFile(String path, String qualifiedName, String entryName, String content) {
      if (myResult.isEmpty()) {
        myResult = content;
      }
    }

    @Override
    public void saveFolder(String path) { }

    @Override
    public void copyFile(String source, String path, String entryName) { }

    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) { }

    @Override
    public void saveDirEntry(String path, String archiveName, String entryName) { }

    @Override
    public void copyEntry(String source, String path, String archiveName, String entry) { }

    @Override
    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) { }

    @Override
    public void closeArchive(String path, String archiveName) { }
  }
}
