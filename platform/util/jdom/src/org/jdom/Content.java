/*--

 Copyright (C) 2007-2012 Jason Hunter & Brett McLaughlin.
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

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * Superclass for JDOM objects which can be legal child content
 * of {@link Parent} nodes.
 *
 * @author Bradley S. Huffman
 * @author Jason Hunter
 * @author Rolf Lear
 * @see Comment
 * @see DocType
 * @see Element
 * @see EntityRef
 * @see Parent
 * @see ProcessingInstruction
 * @see Text
 */
public abstract class Content extends CloneBase implements Serializable {
  /**
   * An enumeration useful for identifying content types without
   * having to do <code>instanceof</code> type conditionals.
   * <p>
   * <code>instanceof</code> is not a particularly safe way to test content
   * in JDOM because CDATA extends Text ( a CDATA instance is an
   * <code>instanceof</code> Text).
   * <p>
   * This CType enumeration provides a direct and sure mechanism for
   * identifying JDOM content.
   * <p>
   * These can be used in switch-type statements too (which is much neater
   * than chained if (instanceof) else if (instanceof) else .... expressions):
   * <p>
   * <pre>
   *    switch(content.getCType()) {
   *        case Text :
   *             .....
   *             break;
   *        case CDATA :
   *             .....
   *             break;
   *        ....
   *    }
   * </pre>
   *
   * @author Rolf Lear
   * @since JDOM2
   */
  public enum CType {
    /**
     * Represents {@link Comment} content
     */
    Comment,
    /**
     * Represents {@link Element} content
     */
    Element,
    /**
     * Represents {@link ProcessingInstruction} content
     */
    ProcessingInstruction,
    /**
     * Represents {@link EntityRef} content
     */
    EntityRef,
    /**
     * Represents {@link Text} content
     */
    Text,
    /**
     * Represents {@link CDATA} content
     */
    CDATA,
    /**
     * Represents {@link DocType} content
     */
    DocType
  }

  /**
   * The parent {@link Parent} of this Content.
   * Note that the field is not serialized, thus deserialized Content
   * instances are 'detached'
   */
  protected transient Parent parent = null;
  /**
   * The content type enumerate value for this Content
   *
   * @serialField This is an Enum, and cannot be null.
   */
  protected final CType ctype;

  /**
   * Create a new Content instance of a particular type.
   *
   * @param type The {@link CType} of this Content
   */
  protected Content(CType type) {
    ctype = type;
  }


  /**
   * All content has an enumerated type expressing the type of content.
   * This makes it possible to use switch-type statements on the content.
   *
   * @return A CType enumerated value representing this content.
   */
  public final CType getCType() {
    return ctype;
  }

  /**
   * Detaches this child from its parent or does nothing if the child
   * has no parent.
   * <p>
   * This method can be overridden by particular Content subclasses to return
   * a specific type of Content (co-variant return type). All overriding
   * subclasses <b>must</b> call <code>super.detach()</code>;
   *
   * @return this child detached
   */
  public Content detach() {
    if (parent != null) {
      parent.removeContent(this);
    }
    return this;
  }

  /**
   * Return this child's parent, or null if this child is currently
   * not attached. The parent can be either an {@link Element}
   * or a {@link Document}.
   * <p>
   * This method can be overridden by particular Content subclasses to return
   * a specific type of Parent (co-variant return type). All overriding
   * subclasses <b>must</b> call <code>super.getParent()</code>;
   *
   * @return this child's parent or null if none
   */
  public Parent getParent() {
    return parent;
  }

  /**
   * A convenience method that returns any parent element for this element,
   * or null if the element is unattached or is a root element.  This was the
   * original behavior of getParent() in JDOM Beta 9 which began returning
   * Parent in Beta 10.  This method provides a convenient upgrade path for
   * JDOM Beta 10 and 1.0 users.
   *
   * @return the containing Element or null if unattached or a root element
   */
  final public Element getParentElement() {
    Parent parent = this.parent;
    return (Element)((parent instanceof Element) ? parent : null);
  }

  /**
   * Sets the parent of this Content. The caller is responsible for removing
   * any pre-existing parentage.
   * <p>
   * This method can be overridden by particular Content subclasses to return
   * a specific type of Content (co-variant return type). All overriding
   * subclasses <b>must</b> call <code>super.setParent(Parent)</code>;
   *
   * @param parent new parent element
   * @return the target element
   */
  protected Content setParent(Parent parent) {
    this.parent = parent;
    return this;
  }

  /**
   * Return this child's owning document or null if the branch containing
   * this child is currently not attached to a document.
   *
   * @return this child's owning document or null if none
   */
  public @Nullable Document getDocument() {
    return parent == null ? null : parent.getDocument();
  }

  /**
   * Returns the XPath 1.0 string value of this child.
   *
   * @return xpath string value of this child.
   */
  public abstract String getValue();

  @Override
  public Content clone() {
    Content c = (Content)super.clone();
    c.parent = null;
    return c;
  }
}
