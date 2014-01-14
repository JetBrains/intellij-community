package org.groovy.debug.hotswap;

import org.objectweb.asm.*;

import java.lang.String;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;

/**
 * Inspired by GroovyEclipse hot-swap hack (http://jira.codehaus.org/browse/GRECLIPSE-588)
 * Removes all timestamp-related Groovy fields on class loading
 * Also clears Groovy's call site cache
 *
 * @author Andy Clement
 * @author peter
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UnusedDeclaration"})
public class ResetAgent {
  private static final String timeStampFieldStart = "__timeStamp__239_neverHappen";
  private static final byte[] timeStampFieldStartBytes;
  
  static {
    timeStampFieldStartBytes = new byte[timeStampFieldStart.length()];
    for (int i = 0; i < timeStampFieldStart.length(); i++) {
      timeStampFieldStartBytes[i] = (byte)timeStampFieldStart.charAt(i);
    }
  }

  private static boolean initialized;

  public static void premain(String options, Instrumentation inst) {
    // Handle duplicate agents
    if (initialized) {
      return;
    }
    initialized = true;
    inst.addTransformer(new ClassFileTransformer() {
      public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (classBeingRedefined != null) {
          try {
            Field callSiteArrayField = classBeingRedefined.getDeclaredField("$callSiteArray");
            callSiteArrayField.setAccessible(true);
            callSiteArrayField.set(null, null);
          } catch (Throwable ignored) {
          }
        }
        return removeTimestampField(classfileBuffer);
      }

    });
  }

  private static boolean matches(byte[] array, byte[] subArray, int start) {
    for (int i = 0; i < subArray.length; i++) {
      if (array[start + i] != subArray[i]) {
        return false;
      }
    }
    return true;
  }

  private static boolean containsSubArray(byte[] array, byte[] subArray) {
    int maxLength = array.length - subArray.length;
    for (int i = 0; i < maxLength; i++) {
      if (matches(array, subArray, i)) {
        return true;
      }
    }
    return false;
  }

  private static byte[] removeTimestampField(byte[] newBytes) {
    if (!containsSubArray(newBytes, timeStampFieldStartBytes)) {
      return null;
    }
    

    final boolean[] changed = new boolean[]{false};
    final ClassWriter writer = new ClassWriter(0);
    new ClassReader(newBytes).accept(new TimestampFieldRemover(writer, changed), 0);
    if (changed[0]) {
      return writer.toByteArray();
    }
    return null;
  }

  private static class TimestampFieldRemover extends ClassAdapter {
    private final boolean[] changed;

    public TimestampFieldRemover(ClassWriter writer, boolean[] changed) {
      super(writer);
      this.changed = changed;
    }

    @Override
    public FieldVisitor visitField(int i, String name, String s1, String s2, Object o) {
      if (name.startsWith(timeStampFieldStart)) {
        //remove the field
        changed[0] = true;
        return null;
      }
      return super.visitField(i, name, s1, s2, o);
    }

    @Override
    public MethodVisitor visitMethod(int i, String name, String s1, String s2, String[] strings) {
      final MethodVisitor mw = super.visitMethod(i, name, s1, s2, strings);
      if ("<clinit>".equals(name)) {
        //remove field's static initialization
        return new MethodAdapter(mw) {
          @Override
          public void visitFieldInsn(int opCode, String s, String name, String desc) {
            if (name.startsWith(timeStampFieldStart) && opCode == Opcodes.PUTSTATIC) {
              visitInsn(Type.LONG_TYPE.getDescriptor().equals(desc) ? Opcodes.POP2 : Opcodes.POP);
            } else {
              super.visitFieldInsn(opCode, s, name, desc);
            }
          }
        };
      }
      return mw;
    }
  }
}
