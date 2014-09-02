/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.code;

/*
 *   opc_tableswitch, lookupswitch
 */

public class SwitchInstruction extends Instruction {

  private int[] destinations;

  private int[] values;

  private int defaultdest;

  public SwitchInstruction() {
  }


  public void initInstruction(InstructionSequence seq) {

    int pref = (opcode == CodeConstants.opc_tableswitch ? 3 : 2);
    int len = this.getOperands().length - pref;
    defaultdest = seq.getPointerByRelOffset(this.getOperand(0));

    int low = 0;

    if (opcode == CodeConstants.opc_lookupswitch) {
      len /= 2;
    }
    else {
      low = this.getOperand(1);
    }

    destinations = new int[len];
    values = new int[len];

    for (int i = 0, k = 0; i < len; i++, k++) {
      if (opcode == CodeConstants.opc_lookupswitch) {
        values[i] = this.getOperand(pref + k);
        k++;
      }
      else {
        values[i] = low + k;
      }
      destinations[i] = seq.getPointerByRelOffset(this.getOperand(pref + k));
    }
  }

  public SwitchInstruction clone() {
    SwitchInstruction newinstr = (SwitchInstruction)super.clone();

    newinstr.defaultdest = defaultdest;
    newinstr.destinations = destinations.clone();
    newinstr.values = values.clone();

    return newinstr;
  }

  public int[] getDestinations() {
    return destinations;
  }

  public void setDestinations(int[] destinations) {
    this.destinations = destinations;
  }

  public int getDefaultdest() {
    return defaultdest;
  }

  public void setDefaultdest(int defaultdest) {
    this.defaultdest = defaultdest;
  }

  public int[] getValues() {
    return values;
  }

  public void setValues(int[] values) {
    this.values = values;
  }
}
