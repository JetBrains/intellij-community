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

  private static final Logger LOG = Logger.getInstance(ExtConfiguration.class);
  public boolean USE_INTERNAL_SSH_IMPLEMENTATION = false;

  @Override
  public void readExternal(Element element) throws InvalidDataException {
     DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @Override
  public ExtConfiguration clone() {
    try {
      return (ExtConfiguration) super.clone();
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
      return new ExtConfiguration();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExtConfiguration that = (ExtConfiguration)o;
    if (USE_INTERNAL_SSH_IMPLEMENTATION != that.USE_INTERNAL_SSH_IMPLEMENTATION) return false;
    if (USE_INTERNAL_SSH_IMPLEMENTATION) {
      return true;
    }
    if (!ADDITIONAL_PARAMETERS.equals(that.ADDITIONAL_PARAMETERS)) return false;
    if (!CVS_RSH.equals(that.CVS_RSH)) return false;
    if (!PRIVATE_KEY_FILE.equals(that.PRIVATE_KEY_FILE)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    if (USE_INTERNAL_SSH_IMPLEMENTATION) {
      return 1;
    }
    int result = CVS_RSH.hashCode();
    result = 31 * result + PRIVATE_KEY_FILE.hashCode();
    result = 31 * result + ADDITIONAL_PARAMETERS.hashCode();
    //result = 31 * result + (USE_INTERNAL_SSH_IMPLEMENTATION ? 1 : 0);
    return result;
  }
}
