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

/**
 * An XML comment. Methods allow the user to get and set the text of the
 * comment.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 */
public final class Comment extends Content {
  /**
   * JDOM2 Serialization. In this case, Comment is simple.
   */
  private static final long serialVersionUID = 200L;

  /**
   * Text of the <code>Comment</code>
   */
  private String text;

  /**
   * This creates the comment with the supplied text.
   *
   * @param text <code>String</code> content of comment.
   */
  public Comment(String text) {
    super(CType.Comment);
    setText(text);
  }


  /**
   * Returns the XPath 1.0 string value of this element, which is the
   * text of this comment.
   *
   * @return the text of this comment
   */
  @Override
  public String getValue() {
    return text;
  }

  /**
   * This returns the textual data within the <code>Comment</code>.
   *
   * @return <code>String</code> - text of comment.
   */
  public String getText() {
    return text;
  }

  /**
   * This will set the value of the <code>Comment</code>.
   *
   * @param text <code>String</code> text for comment.
   * @return <code>Comment</code> - this Comment modified.
   * @throws IllegalDataException if the given text is illegal for a
   *                              Comment.
   */
  public Comment setText(String text) {
    String reason;
    if ((reason = Verifier.checkCommentData(text)) != null) {
      throw new IllegalDataException(text, "comment", reason);
    }

    this.text = text;
    return this;
  }

  @Override
  public Comment clone() {
    return (Comment)super.clone();
  }

  @Override
  public Comment detach() {
    return (Comment)super.detach();
  }

  @Override
  protected Comment setParent(Parent parent) {
    return (Comment)super.setParent(parent);
  }

  @Override
  public String toString() {
    return "[Comment: " + text + "]";
  }
}
