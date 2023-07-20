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

import java.util.Map;

/**
 * An XML processing instruction. Methods allow the user to obtain the target of
 * the PI as well as its data. The data can always be accessed as a String or,
 * if the data appears akin to an attribute list, can be retrieved as name/value
 * pairs.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 * @author Steven Gould
 */
@Deprecated
public final class ProcessingInstruction extends Content {
  /**
   * JDOM2 Serialization. In this case, ProcessingInstruction is simple.
   */
  private static final long serialVersionUID = 200L;

  /**
   * The target of the PI
   */
  private String target;

  /**
   * The data for the PI as a String
   */
  private String rawData;

  /**
   * This will create a new <code>ProcessingInstruction</code>
   * with the specified target.
   *
   * @param target <code>String</code> target of PI.
   * @throws IllegalTargetException if the given target is illegal
   *                                as a processing instruction name.
   */
  public ProcessingInstruction(String target) {
    this(target, "");
  }

  /**
   * This will create a new <code>ProcessingInstruction</code>
   * with the specified target and data.
   *
   * @param target <code>String</code> target of PI.
   * @param data   <code>Map</code> data for PI, in
   *               name/value pairs
   * @throws IllegalTargetException if the given target is illegal
   *                                as a processing instruction name.
   */
  public ProcessingInstruction(String target, Map<String, String> data) {
    super(CType.ProcessingInstruction);
    setTarget(target);
    setData(data);
  }

  /**
   * This will create a new <code>ProcessingInstruction</code>
   * with the specified target and data.
   *
   * @param target <code>String</code> target of PI.
   * @param data   <code>String</code> data for PI.
   * @throws IllegalTargetException if the given target is illegal
   *                                as a processing instruction name.
   */
  public ProcessingInstruction(String target, String data) {
    super(CType.ProcessingInstruction);
    setTarget(target);
    setData(data);
  }

  /**
   * This will set the target for the PI.
   *
   * @param newTarget <code>String</code> new target of PI.
   */
  private void setTarget(String newTarget) {
    target = newTarget;
  }

  /**
   * Returns the XPath 1.0 string value of this element, which is the
   * data of this PI.
   *
   * @return the data of this PI
   */
  @Override
  public String getValue() {
    return rawData;
  }

  /**
   * This will retrieve the target of the PI.
   *
   * @return <code>String</code> - target of PI.
   */
  public String getTarget() {
    return target;
  }

  /**
   * This will return the raw data from all instructions.
   *
   * @return <code>String</code> - data of PI.
   */
  public String getData() {
    return rawData;
  }

  /**
   * This will set the raw data for the PI.
   *
   * @param data <code>String</code> data of PI.
   * @return <code>ProcessingInstruction</code> - this PI modified.
   */
  public ProcessingInstruction setData(String data) {
    this.rawData = data;
    return this;
  }

  private void setData(Map<String, String> data) {
    String temp = toString(data);

    this.rawData = temp;
  }

  private static String toString(Map<String, String> mapData) {
    StringBuilder stringData = new StringBuilder();

    for (Map.Entry<String, String> me : mapData.entrySet()) {
      stringData.append(me.getKey()).append("=\"").append(me.getValue()).append("\" ");
    }
    // Remove last space, if we did any appending
    if (stringData.length() > 0) {
      stringData.setLength(stringData.length() - 1);
    }

    return stringData.toString();
  }

  @Override
  public String toString() {
    return "[ProcessingInstruction]";
  }

  @Override
  public ProcessingInstruction clone() {
    // target and raw-data are immutable and references copied by
    // Object.clone()

    // Create a new Map object for the clone (since Map isn't Cloneable)
    return (ProcessingInstruction)super.clone();
  }

  @Override
  public ProcessingInstruction detach() {
    return (ProcessingInstruction)super.detach();
  }

  @Override
  protected ProcessingInstruction setParent(Parent parent) {
    return (ProcessingInstruction)super.setParent(parent);
  }

  /**
   * Thrown when a target is supplied in construction of a JDOM {@link
   * ProcessingInstruction}, and that name breaks XML naming conventions.
   */
  private static final class IllegalTargetException extends IllegalArgumentException {
    private IllegalTargetException(String target, String reason) {
      super("The target \"" + target + "\" is not legal for JDOM/XML Processing Instructions: " + reason + ".");
    }
  }
}
