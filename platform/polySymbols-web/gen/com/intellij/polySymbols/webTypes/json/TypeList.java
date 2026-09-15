
package com.intellij.polySymbols.webTypes.json;

import java.util.ArrayList;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = TypeList.MyDeserializer.class)
public class TypeList
    extends ArrayList<Type>
{


    public static class MyDeserializer
        extends ValueDeserializer<TypeList>
    {


        @Override
        public TypeList deserialize(JsonParser parser, DeserializationContext deserializationContext)
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
