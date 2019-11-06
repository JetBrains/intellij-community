/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class ProxySettings implements JDOMExternalizable, Cloneable {
  private static final Logger LOG = Logger.getInstance(ProxySettings.class);

  public static final int HTTP = 0;
  public static final int SOCKS4 = 1;
  public static final int SOCKS5 = 2;

  public boolean USE_PROXY = false;
  public String PROXY_HOST = "";
  public int PROXY_PORT = 8080;
  public int TYPE = HTTP;
  public String LOGIN = "";
  public String PASSWORD = "";

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ProxySettings that = (ProxySettings)o;
    if (USE_PROXY != that.USE_PROXY) return false;
    if (!USE_PROXY) {
      return true;
    }
    return PROXY_PORT == that.PROXY_PORT &&
           TYPE == that.TYPE &&
           LOGIN.equals(that.LOGIN) &&
           PASSWORD.equals(that.PASSWORD) &&
           PROXY_HOST.equals(that.PROXY_HOST);
  }

  @Override
  public int hashCode() {
    if (!USE_PROXY) {
      return 1;
    }
    int result = 31 * PROXY_HOST.hashCode();
    result = 31 * result + PROXY_PORT;
    result = 31 * result + TYPE;
    result = 31 * result + LOGIN.hashCode();
    result = 31 * result + PASSWORD.hashCode();
    return result;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @Override
  public ProxySettings clone() {
    try {
      return (ProxySettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public int getType() {
    return TYPE;
  }

  public String getLogin() {
    return LOGIN;
  }

  public String getPassword() {
    return PASSWORD;
  }
}