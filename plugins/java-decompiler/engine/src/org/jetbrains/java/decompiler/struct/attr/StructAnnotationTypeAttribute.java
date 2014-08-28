package org.jetbrains.java.decompiler.struct.attr;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

public class StructAnnotationTypeAttribute extends StructGeneralAttribute {
	
	public static final int ANNOTATION_TARGET_TYPE_GENERIC_CLASS = 0x00;
	public static final int ANNOTATION_TARGET_TYPE_GENERIC_METHOD = 0x01;
	public static final int ANNOTATION_TARGET_TYPE_EXTENDS_IMPLEMENTS = 0x10;
	public static final int ANNOTATION_TARGET_TYPE_GENERIC_CLASS_BOUND = 0x11;
	public static final int ANNOTATION_TARGET_TYPE_GENERIC_METHOD_BOUND = 0x12;
	public static final int ANNOTATION_TARGET_TYPE_FIELD = 0x13;
	public static final int ANNOTATION_TARGET_TYPE_RETURN = 0x14;
	public static final int ANNOTATION_TARGET_TYPE_RECEIVER = 0x15;
	public static final int ANNOTATION_TARGET_TYPE_FORMAL = 0x16;
	public static final int ANNOTATION_TARGET_TYPE_THROWS = 0x17;
	public static final int ANNOTATION_TARGET_TYPE_LOCAL_VARIABLE = 0x40;
	public static final int ANNOTATION_TARGET_TYPE_RESOURCE_VARIABLE = 0x41;
	public static final int ANNOTATION_TARGET_TYPE_EXCEPTION = 0x42;
	public static final int ANNOTATION_TARGET_TYPE_INSTANCEOF = 0x43;
	public static final int ANNOTATION_TARGET_TYPE_NEW = 0x44;
	public static final int ANNOTATION_TARGET_TYPE_DOUBLECOLON_NEW = 0x45;
	public static final int ANNOTATION_TARGET_TYPE_DOUBLECOLON_ID = 0x46;
	public static final int ANNOTATION_TARGET_TYPE_CAST = 0x47;
	public static final int ANNOTATION_TARGET_TYPE_INVOKATION_CONSTRUCTOR = 0x48;
	public static final int ANNOTATION_TARGET_TYPE_INVOKATION_METHOD = 0x49;
	public static final int ANNOTATION_TARGET_TYPE_GENERIC_DOUBLECOLON_NEW = 0x4A;
	public static final int ANNOTATION_TARGET_TYPE_GENERIC_DOUBLECOLON_ID = 0x4B;
	
	public static final int ANNOTATION_TARGET_UNION_TYPE_PARAMETER = 1;
	public static final int ANNOTATION_TARGET_UNION_SUPERTYPE = 2;
	public static final int ANNOTATION_TARGET_UNION_TYPE_PARAMETER_BOUND = 3;
	public static final int ANNOTATION_TARGET_UNION_EMPTY = 4;
	public static final int ANNOTATION_TARGET_UNION_FORMAL_PARAMETER = 5;
	public static final int ANNOTATION_TARGET_UNION_THROWS = 6;
	public static final int ANNOTATION_TARGET_UNION_LOCALVAR = 7;
	public static final int ANNOTATION_TARGET_UNION_CATCH = 8;
	public static final int ANNOTATION_TARGET_UNION_OFFSET = 9;
	public static final int ANNOTATION_TARGET_UNION_TYPE_ARGUMENT = 10;
	

	List<AnnotationLocation> locations = new ArrayList<AnnotationLocation>();
	List<AnnotationExprent> annotations = new ArrayList<AnnotationExprent>();
	
	public void initContent(ConstantPool pool) {

		super.initContent(pool);
		
		DataInputStream data = new DataInputStream(new ByteArrayInputStream(info));
		
		try {
			
			int ann_number = data.readUnsignedByte();
			for(int i = 0; i < ann_number; i++) {
				locations.add(parseAnnotationLocation(data));
				annotations.add(StructAnnotationAttribute.parseAnnotation(data, pool));
			}
			
		} catch(IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public AnnotationLocation parseAnnotationLocation(DataInputStream data) throws IOException {

		AnnotationLocation ann_location = new AnnotationLocation();
		
		// target type 
		
		ann_location.target_type = data.readUnsignedByte();
		
		// target union 
		
		switch(ann_location.target_type) {
			case ANNOTATION_TARGET_TYPE_GENERIC_CLASS:
			case ANNOTATION_TARGET_TYPE_GENERIC_METHOD:
				ann_location.target_union = ANNOTATION_TARGET_UNION_TYPE_PARAMETER;
				break;
			case ANNOTATION_TARGET_TYPE_EXTENDS_IMPLEMENTS:
				ann_location.target_union = ANNOTATION_TARGET_UNION_SUPERTYPE;
				break;
			case ANNOTATION_TARGET_TYPE_GENERIC_CLASS_BOUND:
			case ANNOTATION_TARGET_TYPE_GENERIC_METHOD_BOUND:
				ann_location.target_union = ANNOTATION_TARGET_UNION_TYPE_PARAMETER_BOUND;
				break;
			case ANNOTATION_TARGET_TYPE_FIELD:
			case ANNOTATION_TARGET_TYPE_RETURN:
			case ANNOTATION_TARGET_TYPE_RECEIVER:
				ann_location.target_union = ANNOTATION_TARGET_UNION_EMPTY;
				break;
			case ANNOTATION_TARGET_TYPE_FORMAL:
				ann_location.target_union = ANNOTATION_TARGET_UNION_FORMAL_PARAMETER;
				break;
			case ANNOTATION_TARGET_TYPE_THROWS:
				ann_location.target_union = ANNOTATION_TARGET_UNION_THROWS;
				break;
			case ANNOTATION_TARGET_TYPE_LOCAL_VARIABLE:
			case ANNOTATION_TARGET_TYPE_RESOURCE_VARIABLE:
				ann_location.target_union = ANNOTATION_TARGET_UNION_LOCALVAR;
				break;
			case ANNOTATION_TARGET_TYPE_EXCEPTION:
				ann_location.target_union = ANNOTATION_TARGET_UNION_CATCH;
				break;
			case ANNOTATION_TARGET_TYPE_INSTANCEOF:
			case ANNOTATION_TARGET_TYPE_NEW:
			case ANNOTATION_TARGET_TYPE_DOUBLECOLON_NEW:
			case ANNOTATION_TARGET_TYPE_DOUBLECOLON_ID:
				ann_location.target_union = ANNOTATION_TARGET_UNION_OFFSET;
				break;
			case ANNOTATION_TARGET_TYPE_CAST:
			case ANNOTATION_TARGET_TYPE_INVOKATION_CONSTRUCTOR:
			case ANNOTATION_TARGET_TYPE_INVOKATION_METHOD:
			case ANNOTATION_TARGET_TYPE_GENERIC_DOUBLECOLON_NEW:
			case ANNOTATION_TARGET_TYPE_GENERIC_DOUBLECOLON_ID:
				ann_location.target_union = ANNOTATION_TARGET_UNION_TYPE_ARGUMENT;
				break;
			default:
				throw new RuntimeException("Unknown target type in a type annotation!");
		}
		
		// target union data
		
		switch(ann_location.target_union) {
			case ANNOTATION_TARGET_UNION_TYPE_PARAMETER:
			case ANNOTATION_TARGET_UNION_FORMAL_PARAMETER:
				ann_location.data = new int[] {data.readUnsignedByte()};
				break;
			case ANNOTATION_TARGET_UNION_SUPERTYPE:
			case ANNOTATION_TARGET_UNION_THROWS:
			case ANNOTATION_TARGET_UNION_CATCH:
			case ANNOTATION_TARGET_UNION_OFFSET:
				ann_location.data = new int[] {data.readUnsignedShort()};
				break;
			case ANNOTATION_TARGET_UNION_TYPE_PARAMETER_BOUND:
				ann_location.data = new int[] {data.readUnsignedByte(), data.readUnsignedByte()};
				break;
			case ANNOTATION_TARGET_UNION_EMPTY:
				break;
			case ANNOTATION_TARGET_UNION_LOCALVAR:
				int table_length = data.readUnsignedShort();
				
				ann_location.data = new int[table_length * 3 + 1];
				ann_location.data[0] = table_length; 
				
				for(int i = 0; i < table_length; ++i) {
					ann_location.data[3 * i + 1] = data.readUnsignedShort();	
					ann_location.data[3 * i + 2] = data.readUnsignedShort();	
					ann_location.data[3 * i + 3] = data.readUnsignedShort();	
				}
				break;
			case ANNOTATION_TARGET_UNION_TYPE_ARGUMENT:
				ann_location.data = new int[] {data.readUnsignedShort(), data.readUnsignedByte()};
		}

		// target path
		
		int path_length = data.readUnsignedByte();
		
		ann_location.target_path_kind = new int[path_length];
		ann_location.target_argument_index = new int[path_length];
		
		for(int i = 0; i < path_length; ++i) {
			ann_location.target_path_kind[i] = data.readUnsignedByte(); 	
			ann_location.target_argument_index[i] = data.readUnsignedByte(); 	
		}
		
		return ann_location;
	}
	
	private static class AnnotationLocation {
		
		public int target_type;
		public int target_union;
		
		public int[] data;
		
		public int[] target_path_kind;
		public int[] target_argument_index;
	}
}

