/*-- 

 Copyright (C) 2000-2012 Jason Hunter & Brett McLaughlin.
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

import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.jdom.JDOMConstants.*;

/**
 * An XML namespace representation, as well as a factory for creating XML
 * namespace objects. All methods on Namespace (including
 * {@link #getNamespace(String)} and {@link #getNamespace(String, String)})
 * are thread-safe.
 *
 * <p>
 * See {@link NamespaceAware} for additional notes on how Namespaces are
 * 'in-scope' in JDOM content, and how those in-scope Namespaces are accessed.
 *
 * @author Brett McLaughlin
 * @author Elliotte Rusty Harold
 * @author Jason Hunter
 * @author Wesley Biggs
 * @author Rolf Lear
 * @see NamespaceAware
 */
public final class Namespace implements Serializable {

  /**
   * Factory list of namespaces.
   * Keys are <i>URI</i>&amp;<i>prefix</i>.
   * Values are Namespace objects
   */

  private static final ConcurrentMap<String, ConcurrentMap<String, Namespace>>
    namespacemap = new ConcurrentHashMap
    <>(512, 0.75f, 64);

  /**
   * Define a <code>Namespace</code> for when <i>not</i> in a namespace
   */
  public static final Namespace NO_NAMESPACE = new Namespace(NS_PREFIX_DEFAULT,
                                                             NS_URI_DEFAULT);

  /**
   * Define a <code>Namespace</code> for the standard xml prefix.
   */
  public static final Namespace XML_NAMESPACE = new Namespace(NS_PREFIX_XML,
                                                              NS_URI_XML);

  private static final Namespace XMLNS_NAMESPACE = new Namespace(NS_PREFIX_XMLNS,
                                                                 NS_URI_XMLNS);

  static {
    // pre-populate the map with the constant namespaces that would
    // otherwise fail validation
    final ConcurrentMap<String, Namespace> nmap =
      new ConcurrentHashMap<>();
    nmap.put(NO_NAMESPACE.getPrefix(), NO_NAMESPACE);
    namespacemap.put(NO_NAMESPACE.getURI(), nmap);

    final ConcurrentMap<String, Namespace> xmap =
      new ConcurrentHashMap<>();
    xmap.put(XML_NAMESPACE.getPrefix(), XML_NAMESPACE);
    namespacemap.put(XML_NAMESPACE.getURI(), xmap);

    final ConcurrentMap<String, Namespace> xnsmap =
      new ConcurrentHashMap<>();
    xnsmap.put(XMLNS_NAMESPACE.getPrefix(), XMLNS_NAMESPACE);
    namespacemap.put(XMLNS_NAMESPACE.getURI(), xnsmap);
  }

  /**
   * This will retrieve (if in existence) or create (if not) a
   * <code>Namespace</code> for the supplied <i>prefix</i> and <i>uri</i>.
   * This method is thread-safe.
   *
   * @param prefix <code>String</code> prefix to map to
   *               <code>Namespace</code>.
   * @param uri    <code>String</code> URI of new <code>Namespace</code>.
   * @return <code>Namespace</code> - ready to use namespace.
   * @throws IllegalNameException if the given prefix and uri make up
   *                              an illegal namespace name.
   * @see Verifier#checkNamespacePrefix(String)
   * @see Verifier#checkNamespaceURI(String)
   */
  public static Namespace getNamespace(final String prefix, final String uri) {

    // This is a rewrite of the JDOM 1 getNamespace() to use
    // java.util.concurrent. The motivation is:
    // 1. avoid having to create a new NamespaceKey for each query.
    // 2. avoid a 'big' synchronisation bottleneck in the Namespace class.
    // 3. no-memory-lookup for pre-existing Namespaces... (avoid 'new' and
    //    most String methods that allocte memory (like trim())

    if (uri == null) {
      if (prefix == null || NS_PREFIX_DEFAULT.equals(prefix)) {
        return NO_NAMESPACE;
      }
      // we have an attempt for some prefix
      // (not "" or it would have found NO_NAMESPACE) on the null URI
      throw new IllegalNameException("", "namespace",
                                     "Namespace URIs must be non-null and non-empty Strings");
    }

    // must have checked for 'null' uri else namespacemap throws NPE
    // do not 'trim' uri's any more see issue #50
    ConcurrentMap<String, Namespace> urimap = namespacemap.get(uri);
    if (urimap == null) {
      // no Namespaces yet with that URI.
      // Ensure proper naming
      String reason;
      if ((reason = Verifier.checkNamespaceURI(uri)) != null) {
        throw new IllegalNameException(uri, "Namespace URI", reason);
      }

      urimap = new ConcurrentHashMap<>();
      final ConcurrentMap<String, Namespace> xmap =
        namespacemap.putIfAbsent(uri, urimap);

      if (xmap != null) {
        // some other thread registered this URI between when we
        // first checked, and when we got a new map created.
        // we must use the already-registered value.
        urimap = xmap;
      }
    }

    // OK, we have a container for the URI, let's search on the prefix.

    Namespace ns = urimap.get(prefix == null ? NS_PREFIX_DEFAULT : prefix);
    if (ns != null) {
      // got one.
      return ns;
    }

    // OK, no namespace yet for that uri/prefix
    // validate the prefix (the uri is already validated).

    if (NS_URI_DEFAULT.equals(uri)) {
      // we have an attempt for some prefix
      // (not "" or it would have found NO_NAMESPACE) on the "" URI
      // note, we have already done this check for 'null' uri above.
      throw new IllegalNameException("", "namespace",
                                     "Namespace URIs must be non-null and non-empty Strings");
    }

    // http://www.w3.org/TR/REC-xml-names/#xmlReserved
    // The erratum to Namespaces in XML 1.0 that suggests this
    // next check is controversial. Not everyone accepts it.
    if (NS_URI_XML.equals(uri)) {
      throw new IllegalNameException(uri, "Namespace URI",
                                     "The " + NS_URI_XML + " must be bound to " +
                                     "only the '" + NS_PREFIX_XML + "' prefix.");
    }

    // http://www.w3.org/TR/REC-xml-names/#xmlReserved
    if (NS_URI_XMLNS.equals(uri)) {
      throw new IllegalNameException(uri, "Namespace URI",
                                     "The " + NS_URI_XMLNS + " must be bound to " +
                                     "only the '" + NS_PREFIX_XMLNS + "' prefix.");
    }

    // no namespace found, we validate the prefix
    final String pfx = prefix == null ? NS_PREFIX_DEFAULT : prefix;

    String reason;

    // http://www.w3.org/TR/REC-xml-names/#xmlReserved
    // checkNamespacePrefix no longer checks for xml prefix
    if (NS_PREFIX_XML.equals(pfx)) {
      // The xml namespace prefix was in the map. attempts to rebind it are illegal
      throw new IllegalNameException(uri, "Namespace prefix",
                                     "The prefix " + NS_PREFIX_XML + " (any case) can only be bound to " +
                                     "only the '" + NS_URI_XML + "' uri.");
    }

    // http://www.w3.org/TR/REC-xml-names/#xmlReserved
    // checkNamespacePrefix no longer checks for xmlns prefix
    if (NS_PREFIX_XMLNS.equals(pfx)) {
      // The xml namespace prefix was in the map. attempts to rebind it are illegal
      throw new IllegalNameException(uri, "Namespace prefix",
                                     "The prefix " + NS_PREFIX_XMLNS + " (any case) can only be bound to " +
                                     "only the '" + NS_URI_XMLNS + "' uri.");
    }

    if ((reason = Verifier.checkNamespacePrefix(pfx)) != null) {
      throw new IllegalNameException(pfx, "Namespace prefix", reason);
    }

    // OK, good bet that we have a new Namespace.
    ns = new Namespace(pfx, uri);
    final Namespace prev = urimap.putIfAbsent(pfx, ns);
    if (prev != null) {
      // someone registered the same namespace as us while we were busy
      // validating. Use their registered copy.
      ns = prev;
    }
    return ns;
  }

  /**
   * The prefix mapped to this namespace
   */
  private final transient String prefix;

  /**
   * The URI for this namespace
   */
  private final transient String uri;

  /**
   * This will retrieve (if in existence) or create (if not) a
   * <code>Namespace</code> for the supplied URI, and make it usable
   * as a default namespace, as no prefix is supplied.
   * This method is thread-safe.
   *
   * @param uri <code>String</code> URI of new <code>Namespace</code>.
   * @return <code>Namespace</code> - ready to use namespace.
   */
  public static Namespace getNamespace(final String uri) {
    return getNamespace(NS_PREFIX_DEFAULT, uri);
  }

  /**
   * This constructor handles creation of a <code>Namespace</code> object
   * with a prefix and URI; it is intentionally left <code>private</code>
   * so that it cannot be invoked by external programs/code.
   *
   * @param prefix <code>String</code> prefix to map to this namespace.
   * @param uri    <code>String</code> URI for namespace.
   */
  private Namespace(final String prefix, final String uri) {
    this.prefix = prefix;
    this.uri = uri;
  }

  /**
   * This returns the prefix mapped to this <code>Namespace</code>.
   *
   * @return <code>String</code> - prefix for this <code>Namespace</code>.
   */
  public String getPrefix() {
    return prefix;
  }

  /**
   * This returns the namespace URI for this <code>Namespace</code>.
   *
   * @return <code>String</code> - URI for this <code>Namespace</code>.
   */
  public String getURI() {
    return uri;
  }

  /**
   * This tests for equality - Two <code>Namespaces</code>
   * are equal if and only if their URIs are byte-for-byte equals.
   *
   * @param ob <code>Object</code> to compare to this <code>Namespace</code>.
   * @return <code>boolean</code> - whether the supplied object is equal to
   * this <code>Namespace</code>.
   */
  @Override
  public boolean equals(final Object ob) {
    if (this == ob) {
      return true;
    }
    if (ob instanceof Namespace) {  // instanceof returns false if null
      return uri.equals(((Namespace)ob).uri);
    }
    return false;
  }

  /**
   * This returns a <code>String</code> representation of this
   * <code>Namespace</code>, suitable for use in debugging.
   *
   * @return <code>String</code> - information about this instance.
   */
  @Override
  public String toString() {
    return "[Namespace: prefix \"" + prefix + "\" is mapped to URI \"" +
           uri + "\"]";
  }

  /**
   * This returns the hash code for the <code>Namespace</code> that conforms
   * to the 'equals()' contract.
   * <p>
   * If two namespaces have the same URI, they are equal and have the same
   * hash code, even if they have different prefixes.
   *
   * @return <code>int</code> - hash code for this <code>Namespace</code>.
   */
  @Override
  public int hashCode() {
    return uri.hashCode();
  }


  /* *****************************************
   * Serialization
   * ***************************************** */

  /**
   * JDOM 2.0.0 Serialization version
   */
  private static final long serialVersionUID = 200L;

  private static final class NamespaceSerializationProxy
    implements Serializable {

    private static final long serialVersionUID = 200L;
    private final String pprefix, puri;

    private NamespaceSerializationProxy(String pprefix, String puri) {
      this.pprefix = pprefix;
      this.puri = puri;
    }

    private Object readResolve() {
      return getNamespace(pprefix, puri);
    }
  }

  /**
   * Serializes Namespace by using a proxy serialization instance.
   *
   * @return the Namespace proxy instance.
   * @serialData The proxy deals with the protocol.
   */
  private Object writeReplace() {
    return new NamespaceSerializationProxy(prefix, uri);
  }

  /**
   * Because Namespace is serialized by proxy, the reading of direct Namespace
   * instances is illegal and prohibited.
   *
   * @return nothing.
   * @throws InvalidObjectException always
   */
  private Object readResolve() throws InvalidObjectException {
    throw new InvalidObjectException(
      "Namespace is serialized through a proxy");
  }
}
