
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = TypeList.MyDeserializer.class)
public class TypeList
    extends ArrayList<Type>
{


    public static class MyDeserializer
        extends JsonDeserializer<TypeList>
    {


        @Override
        public TypeList deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            TypeList result = new TypeList();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken()!= JsonToken.END_ARRAY) {
                    token = parser.currentToken();
                    if ((token == JsonToken.VALUE_STRING)||(token == JsonToken.START_OBJECT)) {
                        result.add(parser.readValueAs(Type.class));
                    } else {
                        deserializationContext.handleUnexpectedToken(Type.class, parser);
                    }
                }
            } else {
                if ((token == JsonToken.VALUE_STRING)||(token == JsonToken.START_OBJECT)) {
                    result.add(parser.readValueAs(Type.class));
                } else {
                    deserializationContext.handleUnexpectedToken(Type.class, parser);
                }
            }
            return result;
        }

    }

}
