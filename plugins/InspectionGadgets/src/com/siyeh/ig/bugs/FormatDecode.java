package com.siyeh.ig.bugs;
// $Header$

import com.intellij.psi.PsiType;

import java.util.ArrayList;
import java.util.DuplicateFormatFlagsException;
import java.util.UnknownFormatConversionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"CollectionDeclaredAsConcreteClass", "ObjectEquality"})
class FormatDecode{
    private static final String FORMAT_SPECIFIER =
            "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

    private static final Pattern fsPattern = Pattern.compile(FORMAT_SPECIFIER);

    private FormatDecode(){
        super();
    }

    private static final Validator ALL_VALIDATOR = new Validator(){
        public boolean valid(PsiType type){
            return true;
        }

        public String type(){
            return "any";
        }
    };

    private static final Validator DATE_VALIDATOR = new Validator(){
        public boolean valid(PsiType type){
            final String text = type.getCanonicalText();

            if(type == PsiType.LONG || "java.lang.Long".equals(text))
                return true;
            if("java.util.Date".equals(text) || "java.util.Calendar".equals(text))
                return true;
            return false;
        }

        public String type(){
            return "Date/Time";
        }
    };
    private static final Validator CHAR_VALIDATOR = new Validator(){
        public boolean valid(PsiType type){
            final String text = type.getCanonicalText();
            if(type == PsiType.CHAR || "java.lang.Character".equals(text))
                return true;
            return false;
        }

        public String type(){
            return "char";
        }
    };
    private static final Validator INT_VALIDATOR = new Validator(){
        public boolean valid(PsiType type){
            final String text = type.getCanonicalText();
            if(type == PsiType.INT || "java.lang.Integer".equals(text))
                return true;
            if(type == PsiType.LONG || "java.lang.Long".equals(text))
                return true;
            if(type == PsiType.SHORT || "java.lang.Short".equals(text))
                return true;
            if(type == PsiType.BYTE || "java.lang.Byte".equals(text))
                return true;
            if("java.math.BigInteger".equals(text))
                return true;
            return false;
        }

        public String type(){
            return "integer type";
        }
    };
    private static final Validator FLOAT_VALIDATOR = new Validator(){
        public boolean valid(PsiType type){
            final String text = type.getCanonicalText();
            if(type == PsiType.DOUBLE || "java.lang.Double".equals(text))
                return true;
            if(type == PsiType.FLOAT || "java.lang.Float".equals(text))
                return true;
            if("java.math.BigDecimal".equals(text))
                return true;
            return false;
        }

        public String type(){
            return "floating point";
        }
    };

    public static Validator[] decode(String line){
        final ArrayList<Validator> args = new ArrayList<Validator>();

        final Matcher m = fsPattern.matcher(line);
        int implicit = 0;
        int pos = 0;
        for(int i = 0; m.find(i); i = m.end()){
            final String posSpec = m.group(1);
            final String flags = m.group(2);
            final String dateSpec = m.group(5);
            final String spec = m.group(6);

            // check this first because it should not affect "implicit"
            if("n".equals(spec) || "%".equals(spec))
                continue;

            if(posSpec != null){
                final String num = posSpec.substring(0, posSpec.length() - 1);
                pos = Integer.parseInt(num) - 1;
            } else if(flags == null || flags.indexOf('<') < 0){
                pos = implicit++;
            }
            // else if the flag has "<" reuse the last pos

            final Validator allowed;
            if(dateSpec != null)   // a t or T
            {
                allowed = DATE_VALIDATOR;
            }
            else{
                switch(Character.toLowerCase(spec.charAt(0))){
                    case 'b':
                    case 'h':
                    case 's':
                        allowed = ALL_VALIDATOR;
                        break;
                    case 'c':
                        allowed = CHAR_VALIDATOR;
                        break;
                    case 'd':
                    case 'o':
                    case 'x':
                        allowed = INT_VALIDATOR;
                        break;
                    case 'e':
                    case 'f':
                    case 'g':
                    case 'a':
                        allowed = FLOAT_VALIDATOR;
                        break;
                    default:
                        throw new UnknownFormatConversionException(m.group());
                }
            }
            argAt(allowed, pos, args);
        }

        return args.toArray(new Validator[args.size()]);
    }

    private static void argAt(Validator val, int pos, ArrayList<Validator> args)
    {
        if(pos < args.size()){
            final Validator old = args.get(pos);
            // it's OK to overwrite ALL with something more specific
            // it's OK to ignore overwrite of something else with ALL or itself
            if(old == ALL_VALIDATOR)
                args.set(pos, val);
            else if(val != ALL_VALIDATOR && val != old)
                throw new DuplicateFormatFlagsException(
                        "requires both " + old.type() + " and " + val.type());
        } else{
            while(pos < args.size())args.add(ALL_VALIDATOR);
            args.add(val);
        }
    }
}