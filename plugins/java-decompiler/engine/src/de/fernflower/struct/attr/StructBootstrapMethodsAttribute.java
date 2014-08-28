package de.fernflower.struct.attr;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.fernflower.struct.consts.ConstantPool;
import de.fernflower.struct.consts.LinkConstant;
import de.fernflower.struct.consts.PooledConstant;

public class StructBootstrapMethodsAttribute extends StructGeneralAttribute {
	
	private List<LinkConstant> method_refs = new ArrayList<LinkConstant>();   
	private List<List<PooledConstant>> method_arguments = new ArrayList<List<PooledConstant>>();   
	
	public void initContent(ConstantPool pool) {

		name = ATTRIBUTE_BOOTSTRAP_METHODS;

		try {
			
			DataInputStream data = new DataInputStream(new ByteArrayInputStream(info, 0, info.length));
	
			int method_number = data.readUnsignedShort();
			
			for(int i = 0; i < method_number; ++i) {
				int bootstrap_method_ref = data.readUnsignedShort();
				int num_bootstrap_arguments = data.readUnsignedShort();
				
				List<PooledConstant> list_arguments = new ArrayList<PooledConstant>(); 
				
				for(int j = 0; j < num_bootstrap_arguments; ++j) {
					int bootstrap_argument_ref = data.readUnsignedShort();
					
					list_arguments.add(pool.getConstant(bootstrap_argument_ref));
				}
				
				method_refs.add(pool.getLinkConstant(bootstrap_method_ref));
				method_arguments.add(list_arguments);
			}
		
		} catch(IOException ex) {
			throw new RuntimeException(ex);
		}
		
	}

	public int getMethodsNumber() {
		return method_refs.size();
	}
	
	public LinkConstant getMethodReference(int index) {
		return method_refs.get(index);
	}

	public List<PooledConstant> getMethodArguments(int index) {
		return method_arguments.get(index);
	}
	
}
