/*--

 Copyright (C) 2011 - 2012 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Utility class that handles constructing a class using reflection, and a
 * no-argument 'default' constructor.
 *
 * @author Rolf Lear
 */
public final class ReflectionConstructor {

  /**
   * Construct a new instance of the named class, and ensure it is cast
   * to the type specified as the targetclass.
   *
   * @param <E>         The generic type of the returned value.
   * @param classname   The class name of the instance to create.
   * @param targetclass The return type of the created instance
   * @return an instantiated class
   * @throws IllegalArgumentException if there is a problem locating the class instance.
   * @throws IllegalStateException    if there is a problem instantiating a class instance.
   */
  public static <E> E construct(String classname, Class<E> targetclass) {
    try {
      Class<?> sclass = Class.forName(classname);
      if (!targetclass.isAssignableFrom(sclass)) {
        throw new ClassCastException("Class '" + classname + "' is not assignable to '" + targetclass.getName() + "'.");
      }
      Constructor<?> constructor = sclass.getConstructor();
      Object o = constructor.newInstance();
      return targetclass.cast(o);
    }
    catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Unable to locate class '" + classname + "'.", e);
    }
    catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Unable to locate class no-arg constructor '" + classname + "'.", e);
    }
    catch (SecurityException | IllegalAccessException e) {
      throw new IllegalStateException("Unable to access class constructor '" + classname + "'.", e);
    }
    catch (InstantiationException e) {
      throw new IllegalStateException("Unable to instantiate class '" + classname + "'.", e);
    }
    catch (InvocationTargetException e) {
      throw new IllegalStateException("Unable to call class constructor '" + classname + "'.", e);
    }
  }
}
