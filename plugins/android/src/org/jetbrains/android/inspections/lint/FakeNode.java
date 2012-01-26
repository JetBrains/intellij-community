package org.jetbrains.android.inspections.lint;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.*;

/**
 * @author Eugene.Kudelevsky
 */
class FakeNode implements Node {
  private final String myText;

  FakeNode(@NotNull String text) {
    myText = text;
  }

  @Override
  public String getNodeName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getNodeValue() throws DOMException {
    return myText;
  }

  @Override
  public void setNodeValue(String nodeValue) throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public short getNodeType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node getParentNode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public NodeList getChildNodes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node getFirstChild() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node getLastChild() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node getPreviousSibling() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node getNextSibling() {
    throw new UnsupportedOperationException();
  }

  @Override
  public NamedNodeMap getAttributes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Document getOwnerDocument() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node insertBefore(Node newChild, Node refChild) throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node removeChild(Node oldChild) throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node appendChild(Node newChild) throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasChildNodes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Node cloneNode(boolean deep) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void normalize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSupported(String feature, String version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getNamespaceURI() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getPrefix() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPrefix(String prefix) throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getLocalName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasAttributes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getBaseURI() {
    throw new UnsupportedOperationException();
  }

  @Override
  public short compareDocumentPosition(Node other) throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getTextContent() throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setTextContent(String textContent) throws DOMException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSameNode(Node other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String lookupPrefix(String namespaceURI) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDefaultNamespace(String namespaceURI) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String lookupNamespaceURI(String prefix) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEqualNode(Node arg) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getFeature(String feature, String version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object setUserData(String key, Object data, UserDataHandler handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getUserData(String key) {
    throw new UnsupportedOperationException();
  }
}
