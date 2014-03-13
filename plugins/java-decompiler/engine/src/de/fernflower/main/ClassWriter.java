/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package de.fernflower.main;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.extern.IFernflowerLogger;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.main.rels.ClassWrapper;
import de.fernflower.main.rels.MethodWrapper;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.exps.AnnotationExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.NewExprent;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.vars.VarTypeProcessor;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.modules.renamer.PoolInterceptor;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructField;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.attr.StructAnnDefaultAttribute;
import de.fernflower.struct.attr.StructAnnotationAttribute;
import de.fernflower.struct.attr.StructAnnotationParameterAttribute;
import de.fernflower.struct.attr.StructConstantValueAttribute;
import de.fernflower.struct.attr.StructExceptionsAttribute;
import de.fernflower.struct.attr.StructGeneralAttribute;
import de.fernflower.struct.attr.StructGenericSignatureAttribute;
import de.fernflower.struct.consts.PrimitiveConstant;
import de.fernflower.struct.gen.FieldDescriptor;
import de.fernflower.struct.gen.MethodDescriptor;
import de.fernflower.struct.gen.VarType;
import de.fernflower.struct.gen.generics.GenericClassDescriptor;
import de.fernflower.struct.gen.generics.GenericFieldDescriptor;
import de.fernflower.struct.gen.generics.GenericMain;
import de.fernflower.struct.gen.generics.GenericMethodDescriptor;
import de.fernflower.struct.gen.generics.GenericType;
import de.fernflower.util.InterpreterUtil;
import de.fernflower.util.VBStyleCollection;

public class ClassWriter {
	
	private static final int[] modval_class = new int[] {CodeConstants.ACC_PUBLIC, CodeConstants.ACC_PROTECTED, CodeConstants.ACC_PRIVATE, 
			CodeConstants.ACC_ABSTRACT, CodeConstants.ACC_STATIC, CodeConstants.ACC_FINAL, CodeConstants.ACC_STRICT};
	
	private static final String[] modstr_class = new String[] {"public ", "protected ", "private ", "abstract ", "static ", "final ", "strictfp "};
	
	private static final int[] modval_field = new int[] {CodeConstants.ACC_PUBLIC, CodeConstants.ACC_PROTECTED, CodeConstants.ACC_PRIVATE, 
			 CodeConstants.ACC_STATIC, CodeConstants.ACC_FINAL, CodeConstants.ACC_TRANSIENT, CodeConstants.ACC_VOLATILE};
	
	private static final String[] modstr_field = new String[] {"public ", "protected ", "private ", "static ", "final ", "transient ", "volatile "};
	
	private static final int[] modval_meth = new int[] {CodeConstants.ACC_PUBLIC, CodeConstants.ACC_PROTECTED, CodeConstants.ACC_PRIVATE, 
			CodeConstants.ACC_ABSTRACT, CodeConstants.ACC_STATIC, CodeConstants.ACC_FINAL, CodeConstants.ACC_SYNCHRONIZED, 
			CodeConstants.ACC_NATIVE, CodeConstants.ACC_STRICT};
	
	private static final String[] modstr_meth = new String[] {"public ", "protected ", "private ", "abstract ", "static ", "final ", "synchronized ", "native ", "strictfp "};
	
	private static final HashSet<Integer> mod_notinterface = new HashSet<Integer>(Arrays.asList(new Integer[] {CodeConstants.ACC_ABSTRACT, CodeConstants.ACC_STATIC}));
	private static final HashSet<Integer> mod_notinterface_fields = new HashSet<Integer>(Arrays.asList(new Integer[] {CodeConstants.ACC_PUBLIC, CodeConstants.ACC_STATIC, CodeConstants.ACC_FINAL}));
	private static final HashSet<Integer> mod_notinterface_meth = new HashSet<Integer>(Arrays.asList(new Integer[] {CodeConstants.ACC_PUBLIC, CodeConstants.ACC_ABSTRACT}));
	
	private ClassReference14Processor ref14processor;
	
	private PoolInterceptor interceptor;
	
	public ClassWriter() {
		ref14processor = new ClassReference14Processor();
		interceptor = DecompilerContext.getPoolInterceptor();
	}

	
	private void invokeProcessors(ClassNode node) {

		ClassWrapper wrapper = node.wrapper;
		StructClass cl = wrapper.getClassStruct();
		
		InitializerProcessor.extractInitializers(wrapper);
		
		if(node.type == ClassNode.CLASS_ROOT && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_CLASS_1_4)) {
			ref14processor.processClassReferences(node);
		}
		
		if(DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (cl.access_flags & CodeConstants.ACC_ENUM) != 0) {
			EnumProcessor.clearEnum(wrapper);
		}

		if(DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ASSERTIONS)) {
			AssertProcessor.buildAssertions(node);
		}

	}
	
	public void classToJava(ClassNode node, BufferedWriter writer, int indent) throws IOException {
		
		ClassWrapper wrapper = node.wrapper;
		StructClass cl = wrapper.getClassStruct();

		ClassNode nodeold = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE);
		DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASSNODE, node);

		// last minute processing
		invokeProcessors(node);
		
		DecompilerContext.getLogger().startWriteClass(cl.qualifiedName);
		
		writeClassDefinition(node, writer, indent);
		
		// methods
		StringWriter strwriter = new StringWriter();
		BufferedWriter bufstrwriter = new BufferedWriter(strwriter);

		boolean firstmt = true;
		boolean mthidden = false;
		
		for(StructMethod mt : cl.getMethods()) {

			int flags = mt.getAccessFlags();

			boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || mt.getAttributes().containsKey("Synthetic");
			boolean isBridge = (flags & CodeConstants.ACC_BRIDGE) != 0;

			if((!isSynthetic || !DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC)) &&
					(!isBridge || !DecompilerContext.getOption(IFernflowerPreferences.REMOVE_BRIDGE)) &&
					!wrapper.getHideMembers().contains(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()))) {
				if(!mthidden && (!firstmt || node.type != ClassNode.CLASS_ANONYMOUS)) {
					bufstrwriter.newLine();
					firstmt = false;
				}

				mthidden = !methodToJava(node, mt, bufstrwriter, indent+1);
			}
		}
		bufstrwriter.flush();

		StringWriter strwriter1 = new StringWriter();
		BufferedWriter bufstrwriter1 = new BufferedWriter(strwriter1);
		
		int fields_count = 0;
		
		boolean enumfields = false;
		
		// fields
		for(StructField fd: cl.getFields()) {
			int flags = fd.access_flags;
			boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || fd.getAttributes().containsKey("Synthetic");
			if((!isSynthetic || !DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC))
					&& !wrapper.getHideMembers().contains(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()))) {
				
				boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0; 
				if(isEnum) {
					if(enumfields) {
						bufstrwriter1.write(",");
						bufstrwriter1.newLine();
					} else {
						enumfields = true;
					}
				} else {
					if(enumfields) {
						bufstrwriter1.write(";");
						bufstrwriter1.newLine();
						enumfields = false;
					}
				}
				
				fieldToJava(wrapper, cl, fd, bufstrwriter1, indent+1);
				fields_count++;
			}
		}
		
		if(enumfields) {
			bufstrwriter1.write(";");
			bufstrwriter1.newLine();
		}
		
		bufstrwriter1.flush();
		
		if(fields_count > 0) {
			writer.newLine();
			writer.write(strwriter1.toString());
			writer.newLine();
		}
		

		// methods
		writer.write(strwriter.toString());

		// member classes
		for(ClassNode inner : node.nested) {
			if(inner.type == ClassNode.CLASS_MEMBER) {
				StructClass innercl = inner.classStruct;
				
				boolean isSynthetic = (innercl.access_flags & CodeConstants.ACC_SYNTHETIC) != 0 || innercl.getAttributes().containsKey("Synthetic");
				if((!isSynthetic || !DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC))
						&& !wrapper.getHideMembers().contains(innercl.qualifiedName)) {
					writer.newLine();
					classToJava(inner, writer, indent+1);
				}
			}
		}
		
		writer.write(InterpreterUtil.getIndentString(indent));
		writer.write("}");
		if(node.type != ClassNode.CLASS_ANONYMOUS) {
			writer.newLine();
		}
		writer.flush();

		DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASSNODE, nodeold);

		DecompilerContext.getLogger().endWriteClass();
	}
	
	private void writeClassDefinition(ClassNode node, BufferedWriter writer, int indent) throws IOException {

		if(node.type == ClassNode.CLASS_ANONYMOUS) {
			writer.write(" {");
			writer.newLine();
		} else {
			
			String indstr = InterpreterUtil.getIndentString(indent);
			
			ClassWrapper wrapper = node.wrapper;
			StructClass cl = wrapper.getClassStruct();
			
			int flags = node.type == ClassNode.CLASS_ROOT?cl.access_flags:node.access;
			boolean isInterface = (flags & CodeConstants.ACC_INTERFACE) != 0;
			boolean isAnnotation = (flags & CodeConstants.ACC_ANNOTATION) != 0;
			boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0;
			
			boolean isDeprecated = cl.getAttributes().containsKey("Deprecated");
			
			if(interceptor != null) {
				String oldname = interceptor.getOldName(cl.qualifiedName);
				if(oldname != null) {
					writer.write(indstr);
					writer.write("// $FF: renamed from: "+getDescriptorPrintOut(oldname, 0));
					writer.newLine();
				}
			}
			
			// class annotations
			List<AnnotationExprent> lstAnn = getAllAnnotations(cl.getAttributes()); 
			for(AnnotationExprent annexpr : lstAnn) {
				if("java/lang/Deprecated".equals(annexpr.getClassname())) {
					isDeprecated = false;
				}
				writer.write(annexpr.toJava(indent));
				writer.newLine();
			}
			
			boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || cl.getAttributes().containsKey("Synthetic");
			
			if(isSynthetic) {
				writer.write(indstr);
				writer.write("// $FF: synthetic class");
				writer.newLine();
			}
			
			if(isDeprecated) {
				if(DecompilerContext.getOption(IFernflowerPreferences.DEPRECATED_COMMENT)) { // special comment for JB
					writer.write(indstr);
					writer.write("/** @deprecated */");
					writer.newLine();
				}
				
				writer.write(indstr);
				writer.write("@Deprecated");
				writer.newLine();
			}
			
			writer.write(indstr);
			
			if(isEnum) {
				// remove abstract and final flags (JLS 8.9 Enums)
				flags &=~CodeConstants.ACC_ABSTRACT;	
				flags &=~CodeConstants.ACC_FINAL;	
			}
			
			for(int i=0;i<modval_class.length;i++) {
				if(!isInterface || !mod_notinterface.contains(modval_class[i])) {
					if((flags & modval_class[i]) != 0) {
						writer.write(modstr_class[i]);
					} 
				}
			}

			if(isEnum) {
				writer.write("enum ");
			}else if(isInterface) {
				if(isAnnotation) {
					writer.write("@");
				}
				writer.write("interface ");
			} else {
				writer.write("class ");
			}
			
			GenericClassDescriptor descriptor = null;
			if(DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
				StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)cl.getAttributes().getWithKey("Signature");
				if(attr != null) {
					descriptor = GenericMain.parseClassSignature(attr.getSignature());
				}
			}
			
			writer.write(node.simpleName);
			if(descriptor != null && !descriptor.fparameters.isEmpty()) {
				writer.write("<");
				for(int i=0;i<descriptor.fparameters.size();i++) {
					if(i>0) {
						writer.write(", ");
					}
					writer.write(descriptor.fparameters.get(i)+" extends ");
					
					List<GenericType> lstBounds = descriptor.fbounds.get(i);
					writer.write(GenericMain.getGenericCastTypeName(lstBounds.get(0)));
					
					for(int j=1;j<lstBounds.size();j++) {
						writer.write(" & " + GenericMain.getGenericCastTypeName(lstBounds.get(j)));
					}
				}
				writer.write(">");
			}
			writer.write(" ");
			
			if(!isEnum && !isInterface && cl.superClass != null) {
				VarType supertype = new VarType(cl.superClass.getString(), true);
				if(!VarType.VARTYPE_OBJECT.equals(supertype)) {
					writer.write("extends ");
					if(descriptor != null) {
						writer.write(GenericMain.getGenericCastTypeName(descriptor.superclass));
					} else {
						writer.write(ExprProcessor.getCastTypeName(supertype));
					}
					writer.write(" ");
				}
			}
			
			if(!isAnnotation) {
				int[] interfaces = cl.getInterfaces();
				if(interfaces.length > 0) {
					writer.write(isInterface?"extends ":"implements ");
					for(int i=0;i<interfaces.length;i++) {
						if(i>0) {
							writer.write(", ");
						}
						if(descriptor != null) {
							writer.write(GenericMain.getGenericCastTypeName(descriptor.superinterfaces.get(i)));
						} else {
							writer.write(ExprProcessor.getCastTypeName(new VarType(cl.getInterface(i), true)));
						}
					}
					writer.write(" ");
				}
			}
			
			writer.write("{");
			writer.newLine();
		}
		
	}
	
	private void fieldToJava(ClassWrapper wrapper, StructClass cl, StructField fd, BufferedWriter writer, int indent) throws IOException {
		
		String indstr = InterpreterUtil.getIndentString(indent);
		
		boolean isInterface = (cl.access_flags & CodeConstants.ACC_INTERFACE) != 0;
		int flags = fd.access_flags;

		if(interceptor != null) {
			String oldname = interceptor.getOldName(cl.qualifiedName+" "+fd.getName()+" "+fd.getDescriptor());
			if(oldname != null) {
				String[] element = oldname.split(" ");
				
				writer.write(indstr);
				writer.write("// $FF: renamed from: "+element[1]+" "+getDescriptorPrintOut(element[2], 1));
				writer.newLine();
			}
		}
		
		boolean isDeprecated = fd.getAttributes().containsKey("Deprecated");
		
		// field annotations
		List<AnnotationExprent> lstAnn = getAllAnnotations(fd.getAttributes()); 
		for(AnnotationExprent annexpr : lstAnn) {
			if("java/lang/Deprecated".equals(annexpr.getClassname())) {
				isDeprecated = false;
			}
			writer.write(annexpr.toJava(indent));
			writer.newLine();
		}
		
		boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || fd.getAttributes().containsKey("Synthetic");
		boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0;
		
		if(isSynthetic) {
			writer.write(indstr);
			writer.write("// $FF: synthetic field");
			writer.newLine();
		}
		
		if(isDeprecated) {
			if(DecompilerContext.getOption(IFernflowerPreferences.DEPRECATED_COMMENT)) { // special comment for JB
				writer.write(indstr);
				writer.write("/** @deprecated */");
				writer.newLine();
			}
			
			writer.write(indstr);
			writer.write("@Deprecated");
			writer.newLine();
		}

		writer.write(indstr);
		
		if(!isEnum) {
			for(int i=0;i<modval_field.length;i++) {
				if(!isInterface || !mod_notinterface_fields.contains(modval_field[i])) {
					if((flags & modval_field[i]) != 0) {
						writer.write(modstr_field[i]);
					}
				}
			}
		}
		
		VarType fieldType = new VarType(fd.getDescriptor(), false); 
		
		GenericFieldDescriptor descriptor = null;
		if(DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
			StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)fd.getAttributes().getWithKey("Signature");
			if(attr != null) {
				descriptor = GenericMain.parseFieldSignature(attr.getSignature());
			}
		}
		
		if(!isEnum) {
			if(descriptor != null) {
				writer.write(GenericMain.getGenericCastTypeName(descriptor.type));
			} else {
				writer.write(ExprProcessor.getCastTypeName(fieldType));
			}
			writer.write(" ");
		}

		writer.write(fd.getName());
		
		Exprent initializer;
		if((flags & CodeConstants.ACC_STATIC) != 0) {
			initializer = wrapper.getStaticFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
		} else {
			initializer = wrapper.getDynamicFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
		}
		 
		if(initializer != null) {
			if(isEnum && initializer.type == Exprent.EXPRENT_NEW) {
				NewExprent nexpr = (NewExprent)initializer;
				nexpr.setEnumconst(true);
				writer.write(nexpr.toJava(indent));
			} else {
				writer.write(" = ");
				writer.write(initializer.toJava(indent));
			}
		} else if((flags & CodeConstants.ACC_FINAL) != 0 && (flags & CodeConstants.ACC_STATIC) != 0) {
			StructConstantValueAttribute attr = (StructConstantValueAttribute)fd.getAttributes().getWithKey(StructGeneralAttribute.ATTRIBUTE_CONSTANT_VALUE);
			if(attr != null) {
				PrimitiveConstant cnst = cl.getPool().getPrimitiveConstant(attr.getIndex());
				writer.write(" = ");
				writer.write(new ConstExprent(fieldType, cnst.value).toJava(indent));
			}
		}
		
		if(!isEnum) {
			writer.write(";");
			writer.newLine();
		}
		
	}
	
	private boolean methodToJava(ClassNode node, StructMethod mt, BufferedWriter writer, int indent) throws IOException {

		ClassWrapper wrapper = node.wrapper;
		StructClass cl = wrapper.getClassStruct();
		
		MethodWrapper meth = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());
		
		MethodWrapper methold = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
		DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, meth);
		
		boolean isInterface = (cl.access_flags & CodeConstants.ACC_INTERFACE) != 0;
		boolean isAnnotation = (cl.access_flags & CodeConstants.ACC_ANNOTATION) != 0;

		boolean isDeprecated = mt.getAttributes().containsKey("Deprecated");
		
		String indstr = InterpreterUtil.getIndentString(indent);
		boolean clinit = false, init = false, dinit = false;
		
		MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
		
		StringWriter strwriter = new StringWriter();
		BufferedWriter bufstrwriter = new BufferedWriter(strwriter); 
		
		int flags = mt.getAccessFlags();
		if((flags & CodeConstants.ACC_NATIVE) != 0) {
			flags &= ~CodeConstants.ACC_STRICT; // compiler bug: a strictfp class sets all methods to strictfp 
		}
		
		if("<clinit>".equals(mt.getName())) {
			flags &= CodeConstants.ACC_STATIC; // ingnore all modifiers except 'static' in a static initializer 
		}
		
		if(interceptor != null) {
			String oldname = interceptor.getOldName(cl.qualifiedName+" "+mt.getName()+" "+mt.getDescriptor());
			if(oldname != null) {
				String[] element = oldname.split(" ");
				
				bufstrwriter.write(indstr);
				bufstrwriter.write("// $FF: renamed from: "+element[1]+" "+getDescriptorPrintOut(element[2], 2));
				bufstrwriter.newLine();
			}
		}
		
		// method annotations
		List<AnnotationExprent> lstAnn = getAllAnnotations(mt.getAttributes()); 
		for(AnnotationExprent annexpr : lstAnn) {
			if("java/lang/Deprecated".equals(annexpr.getClassname())) {
				isDeprecated = false;
			}
			bufstrwriter.write(annexpr.toJava(indent));
			bufstrwriter.newLine();
		}

		boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || mt.getAttributes().containsKey("Synthetic");
		boolean isBridge = (flags & CodeConstants.ACC_BRIDGE) != 0;
		
		if(isSynthetic) {
			bufstrwriter.write(indstr);
			bufstrwriter.write("// $FF: synthetic method");
			bufstrwriter.newLine();
		}

		if(isBridge) {
			bufstrwriter.write(indstr);
			bufstrwriter.write("// $FF: bridge method");
			bufstrwriter.newLine();
		}
		
		if(isDeprecated) {
			if(DecompilerContext.getOption(IFernflowerPreferences.DEPRECATED_COMMENT)) { // special comment for JB
				writer.write(indstr);
				writer.write("/** @deprecated */");
				writer.newLine();
			}
			
			bufstrwriter.write(indstr);
			bufstrwriter.write("@Deprecated");
			bufstrwriter.newLine();
		}
		
		bufstrwriter.write(indstr);
		for(int i=0;i<modval_meth.length;i++) {
			if(!isInterface || !mod_notinterface_meth.contains(modval_meth[i])) {
				if((flags & modval_meth[i]) != 0) {
					bufstrwriter.write(modstr_meth[i]);
				}
			}
		}
		
		// 'default' modifier (Java 8)
		if(isInterface && mt.containsCode()) {
			bufstrwriter.write("default ");
		}

		GenericMethodDescriptor descriptor = null;
		if(DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
			StructGenericSignatureAttribute attr = (StructGenericSignatureAttribute)mt.getAttributes().getWithKey("Signature");
			if(attr != null) {
				descriptor = GenericMain.parseMethodSignature(attr.getSignature());
				if(md.params.length != descriptor.params.size()) {
					DecompilerContext.getLogger().writeMessage("Inconsistent generic signature in method "+mt.getName()+" "+mt.getDescriptor(), IFernflowerLogger.WARNING);
					descriptor = null;
				}
			}
		}
		
		String name = mt.getName();
		if("<init>".equals(name)) {
			if(node.type == ClassNode.CLASS_ANONYMOUS) {
				name = "";
				dinit = true;
			} else {
				name = node.simpleName;
				init = true;
			}
		} else if("<clinit>".equals(name)) {
			name = "";
			clinit = true;
		}
		
		boolean throwsExceptions = false;
		
		int param_count_explicit = 0;
		
		if(!clinit && !dinit) {
			
			boolean thisvar = (mt.getAccessFlags() & CodeConstants.ACC_STATIC) == 0;
		
			// formal type parameters
			if(descriptor != null && !descriptor.fparameters.isEmpty()) {
				bufstrwriter.write("<");
				for(int i=0;i<descriptor.fparameters.size();i++) {
					if(i>0) {
						bufstrwriter.write(", ");
					}
					bufstrwriter.write(descriptor.fparameters.get(i)+" extends ");
					
					List<GenericType> lstBounds = descriptor.fbounds.get(i);
					bufstrwriter.write(GenericMain.getGenericCastTypeName(lstBounds.get(0)));
					
					for(int j=1;j<lstBounds.size();j++) {
						bufstrwriter.write(" & " + GenericMain.getGenericCastTypeName(lstBounds.get(j)));
					}
				}
				bufstrwriter.write("> ");
			}
			
			if(!init) {
				if(descriptor != null) {
					bufstrwriter.write(GenericMain.getGenericCastTypeName(descriptor.ret));
				} else {
					bufstrwriter.write(ExprProcessor.getCastTypeName(md.ret));
				}
				bufstrwriter.write(" ");
			}

			bufstrwriter.write(name);
			bufstrwriter.write("(");
			
			// parameter annotations
			List<List<AnnotationExprent>> lstParAnn = getAllParameterAnnotations(mt.getAttributes()); 
			
			List<VarVersionPaar> signFields = meth.signatureFields;
			
			// compute last visible parameter
			int lastparam_index = -1;
			for(int i=0;i<md.params.length;i++) {
				if(signFields == null || signFields.get(i) == null) {
					lastparam_index = i;
				}
			}
			
			boolean firstpar = true;
			int index = thisvar?1:0;
			for(int i=0;i<md.params.length;i++) {
				if(signFields == null || signFields.get(i) == null) {
					
					if(!firstpar) {
						bufstrwriter.write(", ");
					}
					
					if(lstParAnn.size() > i) {
						List<AnnotationExprent> annotations = lstParAnn.get(i); 
						for(int j=0;j<annotations.size();j++) {
							AnnotationExprent annexpr = annotations.get(j);
							if(annexpr.getAnnotationType() == AnnotationExprent.ANNOTATION_NORMAL) {
								bufstrwriter.newLine();
								bufstrwriter.write(annexpr.toJava(indent+1));
							} else {
								bufstrwriter.write(annexpr.toJava(0));
							}
							bufstrwriter.write(" ");
						}
					}
					
					if(meth.varproc.getVarFinal(new VarVersionPaar(index, 0)) == VarTypeProcessor.VAR_FINALEXPLICIT) {
						bufstrwriter.write("final ");
					}

					
					if(descriptor != null) {
						GenericType partype = descriptor.params.get(i);

						boolean isVarArgs = (i == lastparam_index && (mt.getAccessFlags() & CodeConstants.ACC_VARARGS) != 0
								&& partype.arraydim > 0);

						if(isVarArgs) {
							partype.arraydim--;
						}						
						
						String strpartype = GenericMain.getGenericCastTypeName(partype);
						if(ExprProcessor.UNDEFINED_TYPE_STRING.equals(strpartype) && 
								DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
							strpartype = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
						}

						bufstrwriter.write(strpartype);
						
						if(isVarArgs) {
							bufstrwriter.write(" ...");
						}
						
					} else {
						VarType partype = md.params[i].copy();
						
						boolean isVarArgs = (i == lastparam_index && (mt.getAccessFlags() & CodeConstants.ACC_VARARGS) != 0
								&& partype.arraydim > 0);
						
						if(isVarArgs) {
							partype.decArrayDim();
						}						
						
						String strpartype = ExprProcessor.getCastTypeName(partype);
						if(ExprProcessor.UNDEFINED_TYPE_STRING.equals(strpartype) && 
								DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
							strpartype = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
						}

						bufstrwriter.write(strpartype);
						
						if(isVarArgs) {
							bufstrwriter.write(" ...");
						}
					}
					
					bufstrwriter.write(" ");
					String parname = meth.varproc.getVarName(new VarVersionPaar(index, 0));
					bufstrwriter.write(parname==null?"param"+index:parname); // null iff decompiled with errors
					firstpar = false;
					param_count_explicit++;
				}
				
				index+=md.params[i].stack_size;
			}
			
			bufstrwriter.write(")");
			
			StructExceptionsAttribute attr = (StructExceptionsAttribute)mt.getAttributes().getWithKey("Exceptions");
			if((descriptor!=null && !descriptor.exceptions.isEmpty()) || attr != null) {
				throwsExceptions = true;
				bufstrwriter.write(" throws ");
				
				for(int i=0;i<attr.getThrowsExceptions().size();i++) {
					if(i>0) {
						bufstrwriter.write(", ");
					}
					if(descriptor!=null && !descriptor.exceptions.isEmpty()) {
						bufstrwriter.write(GenericMain.getGenericCastTypeName(descriptor.exceptions.get(i)));
					} else {
						VarType exctype = new VarType(attr.getExcClassname(i, cl.getPool()), true);
						bufstrwriter.write(ExprProcessor.getCastTypeName(exctype));
					}
				}
			} 
		}
		
		boolean hidemethod = false;
		
		if((flags & (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_NATIVE)) != 0) { // native or abstract method (explicit or interface)
			if(isAnnotation) {
				StructAnnDefaultAttribute attr = (StructAnnDefaultAttribute)mt.getAttributes().getWithKey("AnnotationDefault");
				if(attr != null) {
					bufstrwriter.write(" default ");
					bufstrwriter.write(attr.getDefaultValue().toJava(indent+1));
				}
			}
			
			bufstrwriter.write(";");
			bufstrwriter.newLine();
		} else {
			if(!clinit && !dinit) {
				bufstrwriter.write(" ");
			}
			bufstrwriter.write("{");
			bufstrwriter.newLine();
			
			RootStatement root = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;
			
			if(root != null && !meth.decompiledWithErrors) { // check for existence 
				try {
					String code = root.toJava(indent+1);

					boolean singleinit = false;
					if(init && param_count_explicit == 0 && !throwsExceptions && DecompilerContext.getOption(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR)) {
						int init_counter = 0; 
						for(MethodWrapper mth : wrapper.getMethods()) {
							if("<init>".equals(mth.methodStruct.getName())) {
								init_counter++;
							}
						}
						singleinit = (init_counter == 1);
					}

					hidemethod = (clinit || dinit || singleinit) && code.length() == 0;

					bufstrwriter.write(code);
				} catch(Throwable ex) {
					if(DecompilerContext.getLogger().getShowStacktrace()) {
						ex.printStackTrace();
					}
					
					DecompilerContext.getLogger().writeMessage("Method "+mt.getName()+" "+mt.getDescriptor()+" couldn't be written.", IFernflowerLogger.ERROR);
					meth.decompiledWithErrors = true;
				}
			} 
			
			if(meth.decompiledWithErrors) {
				bufstrwriter.write(InterpreterUtil.getIndentString(indent+1));
				bufstrwriter.write("// $FF: Couldn't be decompiled");
				bufstrwriter.newLine();
			}
			
			bufstrwriter.write(indstr+"}");
			bufstrwriter.newLine();
		}
		
		bufstrwriter.flush();
		
		if(!hidemethod) {
			writer.write(strwriter.toString());
		}
		
		DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methold);
		
		return !hidemethod;
	}
	
	private List<AnnotationExprent> getAllAnnotations(VBStyleCollection<StructGeneralAttribute, String> attributes) {
		
		String[] annattrnames = new String[] {StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS, 
				StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS};	
		
		List<AnnotationExprent> lst = new ArrayList<AnnotationExprent>();
		
		for(String attrname : annattrnames) {
			StructAnnotationAttribute attr = (StructAnnotationAttribute)attributes.getWithKey(attrname);
			if(attr != null) {
				lst.addAll(attr.getAnnotations());
			}
		}
		
		return lst;
	}
	
	private List<List<AnnotationExprent>> getAllParameterAnnotations(VBStyleCollection<StructGeneralAttribute, String> attributes) {
		
		String[] annattrnames = new String[] {StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, 
				StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS};	
		
		List<List<AnnotationExprent>> ret = new ArrayList<List<AnnotationExprent>>();
		
		for(String attrname : annattrnames) {
			StructAnnotationParameterAttribute attr = (StructAnnotationParameterAttribute)attributes.getWithKey(attrname);
			if(attr != null) {
				for(int i=0;i<attr.getParamAnnotations().size();i++) {
					List<AnnotationExprent> lst = new ArrayList<AnnotationExprent>();
					boolean isnew = (ret.size()<=i); 
					
					if(!isnew) {
						lst = ret.get(i);
					}
					lst.addAll(attr.getParamAnnotations().get(i));
					
					if(isnew) {
						ret.add(lst);
					} else {
						ret.set(i, lst);
					}
				}
			}
		}
		
		return ret;
	}
	
	private String getDescriptorPrintOut(String descriptor, int element) {
	
		switch(element) {
		case 0: // class
			return ExprProcessor.buildJavaClassName(descriptor);
		case 1: // field
			return getTypePrintOut(FieldDescriptor.parseDescriptor(descriptor).type);
		case 2: // method
		default:
			MethodDescriptor md = MethodDescriptor.parseDescriptor(descriptor);
			
			StringBuilder buffer = new StringBuilder("(");
			
			boolean first = true;
			for(VarType partype : md.params) {
				if(first) {
					first = false;
				} else {
					buffer.append(", ");
				}
				buffer.append(getTypePrintOut(partype));
			}
			buffer.append(") ");
			buffer.append(getTypePrintOut(md.ret));
			
			return buffer.toString();
		}
	}
	
	private String getTypePrintOut(VarType type) {
		String strtype = ExprProcessor.getCastTypeName(type, false);
		if(ExprProcessor.UNDEFINED_TYPE_STRING.equals(strtype) && 
				DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
			strtype = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT, false);
		}
		return strtype;
	}
}
