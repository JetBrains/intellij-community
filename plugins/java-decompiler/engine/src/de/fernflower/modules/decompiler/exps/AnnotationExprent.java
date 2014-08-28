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

package de.fernflower.modules.decompiler.exps;

import java.util.List;

import de.fernflower.main.DecompilerContext;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.util.InterpreterUtil;


public class AnnotationExprent extends Exprent {
	
	public static final int ANNOTATION_NORMAL = 1;
	public static final int ANNOTATION_MARKER = 2;
	public static final int ANNOTATION_SINGLE_ELEMENT = 3;


	private String classname;
	
	private List<String> parnames;
	
	private List<Exprent> parvalues;
	
	{
		this.type = EXPRENT_ANNOTATION;
	}
	
	public AnnotationExprent(String classname, List<String> parnames, List<Exprent> parvalues) {
		this.classname = classname;
		this.parnames = parnames;
		this.parvalues = parvalues;
	}
	
	public String toJava(int indent) {
		
		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		StringBuilder buffer = new StringBuilder();
		String indstr = InterpreterUtil.getIndentString(indent);
		
		buffer.append(indstr);
		buffer.append("@");
		buffer.append(DecompilerContext.getImpcollector().getShortName(ExprProcessor.buildJavaClassName(classname)));
		
		if(!parnames.isEmpty()) {
			buffer.append("(");
			if(parnames.size() == 1 && "value".equals(parnames.get(0))) {
				buffer.append(parvalues.get(0).toJava(indent+1));
			} else {
				String indstr1 = InterpreterUtil.getIndentString(indent+1);
				
				for(int i=0;i<parnames.size();i++) {
					buffer.append(new_line_separator+indstr1);
					buffer.append(parnames.get(i));
					buffer.append(" = ");
					buffer.append(parvalues.get(i).toJava(indent+2));
					
					if(i<parnames.size()-1) {
						buffer.append(",");
					}
				}
				buffer.append(new_line_separator+indstr);
			}
			
			buffer.append(")");
		}
		
		return buffer.toString();
	}
	
	public int getAnnotationType() {
		
		if(parnames.isEmpty()) {
			return ANNOTATION_MARKER;
		} else {
			if(parnames.size() == 1 && "value".equals(parnames.get(0))) {
				return ANNOTATION_SINGLE_ELEMENT;
			} else {
				return ANNOTATION_NORMAL;
			}
		}
	}

	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof AnnotationExprent)) return false;

    AnnotationExprent ann = (AnnotationExprent)o;
    return classname.equals(ann.classname) &&
        InterpreterUtil.equalLists(parnames, ann.parnames) &&
        InterpreterUtil.equalLists(parvalues, ann.parvalues);
  }

	public String getClassname() {
		return classname;
	}
	
}
