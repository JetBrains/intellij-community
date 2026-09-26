/*--

 Copyright (C) 2012 Jason Hunter & Brett McLaughlin.
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

package org.jdom;

import org.jetbrains.annotations.ApiStatus;

/**
 * This simple class just tidies up any cloneable classes. This method deals
 * with any CloneNotSupported exceptions. THis class is package private only.
 *
 * @author Rolf Lear
 */
@ApiStatus.Internal
public class CloneBase implements Cloneable {
  /**
   * Change the permission of the no-arg constructor from public to protected.
   * <p>
   * Otherwise, package-private class's constructor is not really public. Changing this to
   * 'protected' makes this constructor available to all subclasses regardless of the
   * subclass's package. This in turn makes it possible to make all th subclasses of this
   * CloneBase class serializable.
   */
  protected CloneBase() {
    // This constructor is needed to solve issue #88
    // There needs to be a no-arg constructor accessible by all
    // potential subclasses of CloneBase, and 'protected' is actually more
    // accessible than 'public' since this is a package-private class.
  }

  /**
   * Return a deep clone of this instance. Even if this instance has a parent,
   * the returned clone will not.
   * <p>
   * All JDOM core classes are Cloneable, and never throw
   * CloneNotSupportedException. Additionally, all Cloneable JDOM classes
   * return the correct type of instance from this method and there is no
   * need to cast the result (co-variant return value).
   * <p>
   * Subclasses of this should still call super.clone() in their clone method.
   */
  @Override
  protected CloneBase clone() {
    /*
     * Additionally, when you use the concept of 'co-variant return values'
     * you create 'bridge' methods. By way of example, because we change the
     * return type of clone() from Object to CloneBase, Java is forced to
     * put in a 'bridge' method that has an Object return type, even though
     * we never actually call it. <p> This has an impact on the code
     * coverage tool Cobertura, which reports that there is missed code (and
     * there is, the bridge method). It reports it as being '0' calls to the
     * 'class' line (the class line is marked red). By making this CloneBase
     * code do the first level of co-variant return, it is this class which
     * is victim of the Cobertura reporting, not the multiple subclasses
     * (like Attribute, Document, Content, etc.).
     */
    try {
      return (CloneBase)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new IllegalStateException(String.format(
        "Unable to clone class %s which should always support it.",
        this.getClass().getName()), e);
    }
  }
}
