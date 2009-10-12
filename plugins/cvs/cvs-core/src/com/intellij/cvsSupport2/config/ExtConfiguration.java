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
package com.intellij.cvsSupport2.config;

import com.intellij.cvsSupport2.connections.ext.ExtConnection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * author: lesya
 */
public class ExtConfiguration implements JDOMExternalizable, Cloneable{
  public String CVS_RSH = ExtConnection.DEFAULT_RSH;
  public String PRIVATE_KEY_FILE = "";
  public String ADDITIONAL_PARAMETERS = "";

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.config.ExtConfiguration");
  public boolean USE_INTERNAL_SSH_IMPLEMENTATION = false;

  public void readExternal(Element element) throws InvalidDataException {
     DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public ExtConfiguration clone() {
    try {
      return (ExtConfiguration) super.clone();
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
      return new ExtConfiguration();
    }
  }
}
