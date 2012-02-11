/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.designSurface;

import com.intellij.android.designer.componentTree.AndroidTreeDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorPanel extends DesignerEditorPanel {
  private final TreeComponentDecorator myTreeDecorator = new AndroidTreeDecorator();

  public AndroidDesignerEditorPanel(@NotNull Module module, @NotNull VirtualFile file) {
    super(module, file);

    // (temp code) TODO: use platform DOM

    try {
      InputStream stream = file.getInputStream();
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse(stream, new DefaultHandler() {
        RadViewComponent myComponent;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
          myComponent = new RadViewComponent(myComponent, qName);
          if (myRootComponent == null) {
            myRootComponent = myComponent;
          }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
          if (myComponent != null) {
            myComponent = (RadViewComponent)myComponent.getParent();
          }
        }
      });
      stream.close();
    }
    catch (Throwable e) {
      e.printStackTrace(); // TODO
    }
  }

  @Override
  public TreeComponentDecorator getTreeDecorator() {
    return myTreeDecorator;
  }
}