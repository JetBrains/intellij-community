/*--

 $Id: NamespaceStack.java,v 1.13 2004/02/06 09:28:32 jhunter Exp $

 Copyright (C) 2000-2004 Jason Hunter & Brett McLaughlin.
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
package org.jdom.output;

import org.jdom.Namespace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * A non-public utility class used by both <code>{@link XMLOutputter}</code> and
 * <code>{@link SAXOutputter}</code> to manage namespaces in a JDOM Document
 * during output.
 *
 * @author Elliotte Rusty Harolde
 * @author Fred Trimble
 * @author Brett McLaughlin
 * @version $Revision: 1.13 $, $Date: 2004/02/06 09:28:32 $
 */
public class EclipseNamespaceStack {
  static class NamespaceInfo {
    final String prefix;
    final String uri;

    NamespaceInfo(String prefix, String uri) {
      this.prefix = prefix;
      this.uri = uri;
    }

    @Override
    public String toString() {
      return prefix + "&" + uri;
    }
  }

  private final Deque<NamespaceInfo> myNamespaces = new ArrayDeque<>();

  /**
   * This will add a new <code>{@link Namespace}</code>
   * to those currently available.
   *
   * @param ns <code>Namespace</code> to add.
   */
  public void push(Namespace ns) {
    myNamespaces.push(new NamespaceInfo(ns.getPrefix(), ns.getURI()));
  }

  /**
   * This will remove the topmost (most recently added)
   * <code>{@link Namespace}</code>, and return its prefix.
   *
   * @return <code>String</code> - the popped namespace prefix.
   */
  public String pop() {
    return myNamespaces.pop().prefix;
  }

  /**
   * This returns the number of available namespaces.
   *
   * @return <code>int</code> - size of the namespace stack.
   */
  public int size() {
    return myNamespaces.size();
  }

  /**
   * Given a prefix, this will return the namespace URI most
   * recently (topmost) associated with that prefix.
   *
   * @param prefix <code>String</code> namespace prefix.
   * @return <code>String</code> - the namespace URI for that prefix.
   */
  public String getURI(String prefix) {
    Iterator<NamespaceInfo> iterator = myNamespaces.descendingIterator();
    while (iterator.hasNext()) {
      NamespaceInfo ns = iterator.next();
      if (ns.prefix.equals(prefix)) {
        return ns.uri;
      }
    }
    return null;
  }

  /**
   * This will print out the size and current stack, from the
   * most recently added <code>{@link Namespace}</code> to
   * the "oldest," all to <code>System.out</code>.
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    String sep = System.lineSeparator();
    buf.append("Stack: ").append(myNamespaces.size()).append(sep);
    for (NamespaceInfo info : myNamespaces) {
      buf.append(info).append(sep);
    }
    return buf.toString();
  }
}
