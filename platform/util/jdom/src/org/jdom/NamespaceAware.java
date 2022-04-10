/*-- 

 Copyright (C) 2011-2012 Jason Hunter & Brett McLaughlin.
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

import java.util.List;

/**
 * Classes implementing this interface are all sensitive to their
 * {@link Namespace} context. All the core JDOM classes are NamespaceAware
 * ({@link Parent} and subtypes, {@link Content} and subtypes, and
 * {@link Attribute}). You can use the methods that this interface provides
 * to query the Namespace context.
 * <p>
 * JDOM2 introduces a consistency in reporting Namespace context. XML standards
 * do not dictate any conditions on Namespace reporting or ordering, but
 * consistency is valuable for user-friendliness. As a result JDOM2 imposes a
 * useful order on the Namespace context for XML content.
 * <p>
 * The order for Namespace reporting is:
 * <ol>
 * <li>If the item 'has' a Namespace (Element, Attribute) then that Namespace is
 * reported.
 * <li>The remaining Namespaces are reported in alphabetical order by prefix.
 * </ol>
 * <p>
 * The XML namespace (bound to the prefix "xml" - see
 * {@link Namespace#XML_NAMESPACE} ) is always in every scope. It is always
 * introduced in {@link Document}, and in all other NamespaceAware instances it
 * is introduced if that content is detached.
 * <p>
 * See the individualised documentation for each implementing type for
 * additional specific details. The following section is a description of how
 * Namespaces are managed in the Element class.
 * <p>
 * <h2>The Element Namespace Scope</h2>
 * The 'default' Namespace is a source of confusion, but it is simply the
 * Namespace which is in-scope for an Element that has no Namespace prefix
 * (prefix is "" but it could have any Namespace URI). There will always be
 * exactly one Namespace that is in-scope for an element that has no prefix.
 * <p>
 * All Elements are in a Namespace. Elements will be in
 * {@link Namespace#NO_NAMESPACE} unless a different Namespace was supplied as
 * part of the Element Constructor, or later modified by the
 * {@link Element#setNamespace(Namespace)} method.
 * <p>
 * In addition to the Element's Namespace, there could be other Namespaces that
 * are 'in scope' for the Element. The set of Namespaces that are in scope for
 * an Element is the union of five sets:
 * <table>
 *   <tr>
 *     <th valign="top">XML</th>
 *     <td>
 *       There is always exactly one member of this set,
 *       {@link Namespace#XML_NAMESPACE}.
 *       <br>
 *       This set cannot be changed.
 *     </td>
 *   </tr>
 *   <tr>
 *     <th valign="top">Element</th>
 *     <td>
 *       There is always exactly one member of this set, and it can be retrieved
 *       or set with the methods {@link Element#getNamespace()} and
 *       {@link Element#setNamespace(Namespace)} respectively.
 *     </td>
 *   </tr>
 *   <tr>
 *     <th valign="top">Attribute</th>
 *     <td>
 *       This is the set of distinct Namespaces that are used on Attributes. You
 *       can modify the set by adding and removing Attributes to the Element.
 * <p>
 *       <b>NOTE:</b>
 *       The {@link Namespace#NO_NAMESPACE Namespace.NO_NAMESPACE} Namespace is always the
 *       <i>default</i> Namespace for attributes (the Namespace that has no
 *       prefix). Thus there may be a special case with this Namespace, because
 *       if there is a different <i>default</i> Namespace for the Element, then
 *       the Namespace.NO_NAMESPACE Namespace is not part of the Element's in-scope
 *       Namespace set (the Element cannot have two Namespaces in scope with the
 *       same prefix - "").
 *     </td>
 *   </tr>
 *   <tr>
 *     <th valign="top">Additional</th>
 *     <td>
 *       This set is maintained by the two methods {@link Element#addNamespaceDeclaration(Namespace)}
 *       and {@link Element#removeNamespaceDeclaration(Namespace)}. You can get the full set
 *       of additional Namespaces with {@link Element#getAdditionalNamespaces()}
 *     </td>
 *   </tr>
 *   <tr>
 *     <th valign="top">Inherited</th>
 *     <td>
 *       This last set is somewhat dynamic because only those Namespaces on the
 *       parent Element which are not re-defined by this Element will be
 *       inherited. A Namespace is redefined by setting a new Namespace with the
 *       same prefix, but a different URI. If you set a Namespace on the Element
 *       (or add a Namespace declaration or set an Attribute) with the same
 *       prefix as another Namespace that would have been otherwise inherited,
 *       then that other Namespace will no longer be inherited.
 *     </td>
 *   </tr>
 * </table>
 *
 * <p>
 * Since you cannot change the Namespace.XML_NAMESPACE, and the 'inherited' Namespace set
 * is dynamic, the remaining Namespace sets are the most interesting from a JDOM
 * perspective. JDOM validates all modifications that affect the Namespaces in
 * scope for an Element. An IllegalAddException will be thrown if you attempt to
 * add a new Namespace to the in-scope set if a different Namespace with the
 * same prefix is already part of one of these three sets (Element, Attribute,
 * or Additional).
 *
 * @author Rolf Lear
 * @since JDOM2
 */
public interface NamespaceAware {

  /**
   * Obtain a list of all namespaces that are in scope for the current
   * content.
   * <p>
   * The contents of this list will always be the combination of
   * getNamespacesIntroduced() and getNamespacesInherited().
   * <p>
   * See {@link NamespaceAware} documentation for details on what the order of the
   * Namespaces will be in the returned list.
   *
   * @return a read-only list of Namespaces.
   */
  List<Namespace> getNamespacesInScope();

  /**
   * Obtain a list of all namespaces that are introduced to the XML tree by
   * this node. Only Elements and Attributes can introduce namespaces, so all
   * others Content types will return an empty list.
   * <p>
   * The contents of this list will always be a subset (but in the same order)
   * of getNamespacesInScope(), and will never intersect
   * getNamspacesInherited()
   *
   * @return a read-only list of Namespaces.
   */
  List<Namespace> getNamespacesIntroduced();
}
