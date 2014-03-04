package test;

import java.io.File;
import java.util.Date;
import java.util.HashMap;

import de.fernflower.main.decompiler.ConsoleDecompiler;


public class FernflowerTest {

	public static void main(String[] args) {

		try {
			
			//TimerFactory.initialize(new File(".").getCanonicalPath()+"/lib/timer/hrtlib.dll");
			
			Date start = new Date();
			
			
			
			ConsoleDecompiler decompiler = new ConsoleDecompiler(new HashMap<String, Object>(){{put("log", "warn");put("ren", "1");}});
			
			
//			MemoryMonitor.run = true;
//			new Thread(new MemoryMonitor()).start();
			
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\runtime1_4_2_03\\"), true);

						
//			context.addSpace(new File("C:\\revjava\\remote\\data\\input\\"), true);
			
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\smith\\EventProcessor.class", true);
			
			
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514224046740_rggbqdfe\\rh.class", true); // irreducible CFG
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514180223996_zxwdatbw\\TryCatchFinallyClass.class", true);
		
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\temp\\AndroidFileBrowser.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\temp\\SnakeView.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\ru.zip"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\bt1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\CityGuideJava.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\toschart.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\dostoevski.181223.jar"), true);
	
						
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\au.class"), true);  // highly obfuscated exception handlers - sequence!
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\carmobile2.jar"), true);  // highly obfuscated exception handlers - sequence!

			
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\tempjars\\Paris_Nights[sp-c.ru]ygtgJ6.jar"), true); // Heap space
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\tempjars\\PlanetN.jar"), true);
			
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\PasswordDefender1.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\PasswordDefender2.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\Emyoolay.class"), true);
			
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\h1.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\ba1.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\bolt_pg_0.jar"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\masl\\AbstractProgressNotifier.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\f.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\searchTab1_jsp1.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\searchTab1_jsp1_1.class"), true);

			decompiler.addSpace(new File("D:\\temp_c\\workspace\\Fernflower\\bin\\test\\BradTest.class"), true);

//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\cls1209_Object.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\cls540_BaseFunction1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\SwitchInTest.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\al.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\InnerTest.zip"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\InnerTest$1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\InnerTest.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\Context.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\Class$1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\Class.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\SimpleDateFormat1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\z.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\client.jar"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\Manifest1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\MemoryMonitor1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\IrreducibleCFGDeobfuscator$1Node.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\IrreducibleCFGDeobfuscator1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\PrintStreamLogger1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\b.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\toonel_uo-5050.jar"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\toonel_jdo.jar"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\i.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\BasicGroupListUI$a_.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\p1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\v.class"), true); // invalid class structure
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\Game.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\out.zip"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\Logic.class"), true); // long processing time
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\pupkin\\Support2.class"), true); // irreducible CFG
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\CommandExecutor.class"), true); // sophisticated finally

//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\cy1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\gmail-g.jar"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\LDIFImporter1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\d1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\com.sonyericsson.cs_ma3_client_sc_2.9.5.16.jar"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\z1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\FactoryFinder1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\e.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\IndexWriter.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\ar1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\tcf511.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\TryCatchFinallyClass2.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\FileUtils.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\QuicktimeParser$TrakList1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\DoExternalProcessMinwon.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\Main.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\TryCatchFinallyClassForTest.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\PmrCreatePMR.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\RetainSdi(2).zip"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\tcf26x1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\GalleryStorageExtras.class"), true);
			
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\ExprProcessor.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\Compiler1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\ServerManagerImpl.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\RepositoryId1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\ElemApplyTemplates.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\ValueHandlerImpl.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\RequestProcessor.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\PropertyEditorManager.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\JdbcOdbcObject.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\searchTab1_jsp1.class"), true);
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\o_class.class"), true);
			
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\o1_class.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\o_class.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\o_class1.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\FinallyTest.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\J_class.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\icc.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\ca.class"), true);
			
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\e.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\at.class"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\cab.jar"), true);
//			decompiler.addSpace(new File("C:\\Oleg\\dec\\myotr\\"), true); // temp methods of inner classes
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\temp\\google\\"), true); // temp methods of inner classes
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\appengine-api-1.0-sdk-1.2.5-rp.jar"), true); // temp methods of inner classes
//			decompiler.addSpace(new File("C:\\Oleg\\appengine-api-1.0-sdk-1.2.5-rp.jar"), true); // temp methods of inner classes
//			decompiler.addSpace(new File("C:\\Oleg\\e.class"), true); // irreducible CFG - general case
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\e.class"), true); // irreducible CFG - general case
			
			
//			decompiler.addSpace(new File("C:\\Temp\\fernflower\\dec\\opera\\output\\opera4_2.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\ab.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\searchTab1_jsp.class"), true);  // full iteration!
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\b.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\alesa.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\MetaClassRegistryImpl1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\MetaClassRegistryImpl$1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\MetaClassRegistryImpl$2.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\CBZip2OutputStream1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\MicroFMEIBDialog$MicroFMEIBMouse.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\String.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\ArticleBean.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\JdbcOdbcObject1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\jdbc\\odbc\\JdbcOdbcObject.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\RasterPrinterJob1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\print\\RasterPrinterJob.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\runtime1_4_2_03\\javax\\print\\MimeType.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\Application.class"), true); // self reference var replacement
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\JmxBuilderTools.class"), true); // merging blocks!
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\groovy-all-1.6.3.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\ABSInitializeLoginCtx.class"), true); // comparison instructions double, float, long!
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\hm20ipixa.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\jmf.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\Asn1PerEncodeBuffer1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\asn1rt.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\LLString.class"), true);  // wrong array index!
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\lapi.jar"), true);
//			context.addSpace(new File("C:\\revjava\\remote\\data\\input\\20090521062758280_kmxhxsgj\\ab.class"), true);
//			context.addSpace(new File("C:\\revjava\\remote\\data\\input\\20090521023110030_rlkkkxde\\"), true);
//			context.addSpace(new File("C:\\revjava\\remote\\data\\input\\20090515012813271_ktxfuavw\\"), true);
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\g1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\structure101-java-b428.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\NinjaVideoApplet0.3.9.jar"), true);
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\DRMConfigClient.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\DRMConfigClient$1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\DRMConfigClient$ConfigKeys.class"), true);
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\ChartQuery.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\ChartQuery$1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\ChartQuery$ChartQueryListener.class"), true);
			
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\innerprivate\\"), true);
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\awt\\windows\\WScrollPanePeer$Adjustor.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\awt\\windows\\WScrollPanePeer$ScrollEvent.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\awt\\windows\\WScrollPanePeer.class"), true);
			
//			context.addSpace(new File("C:\\revjava\\remote\\data\\input\\20090520235744982_egloemdo\\"), true);
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\conversion.zip"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\toonel.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\msmapi.jar"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\skynin\\"), true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\skynin\\MSMAPI.class", true);
				
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments1.class"), true); // invalid bytecode
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments2.class"), true); // invalid bytecode
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments3.class"), true); // invalid bytecode
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments4.class"), true); // protected range extended!
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments5.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments6.class"), true); // protected range extended!
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments7.class"), true); // highly obfuscated!!
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments8.class"), true); // highly obfuscated!!
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments9.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments10.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments11.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments12.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\experiments\\Experiments13.class"), true); // highly obfuscated!!

//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\com\\sun\\jndi\\ldap\\Connection.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\Connection.class", true);
			
//			context.addClass("TestByte", "D:\\Nonbku\\workspace_3.4\\Fernflower\\bin\\struct\\attr\\MiscTest.class", true);
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\pupkin\\Support2.class", true); // irreducible CFG
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\security\\jgss\\GSSNameImpl.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\awt\\image\\ImageFetcher.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\HttpURLConnection.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\net\\www\\protocol\\http\\HttpURLConnection.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\SocketChannelImpl.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\nio\\ch\\SocketChannelImpl.class", true);
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\rapc\\"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\y\\"), true);
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\layout\\router\\OrthogonalEdgeRouter.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\java\\util\\prefs\\WindowsPreferences.class", true);

			
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\ag1.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\aa1.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\ar.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\aa.class", true);
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\opera-mini-latest-advanced-int.jar"), true);
//			context.addSpace(new File("C:\\revjava\\remote\\data\\input\\20090522175436366_ewwrrmsm\\"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\f1.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\logunov\\"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\logunov\\Lm.class"), true);
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\logunov\\Lm1.class"), true);
			
//			context.addSpace(new File("C:\\Temp\\fernflower\\dec\\balmaster\\"), true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\tcf10.class", true);
//			context.addClass("TestByte", "D:\\Nonbku\\workspace_3.4\\autspl\\bin\\ThreadMain.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\SysEnv.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\e$1.class", true);

			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$InfoPosition.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_j.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_d.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_n.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_g.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$5.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$6.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_l.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_c.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$11.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$12.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_k.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$9.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$8.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$10.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$7.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_f.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_h.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_m.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$3.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$2.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$4.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$ItemEditorOwner.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_b.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_b$_b.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$Theme.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_i.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$1.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\TableEditorFactory$_e.class", true);
			
			
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514095232398_nuopioet\\a5.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514171647485_mjdkkaci\\Main.class", true);
		
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\BIDVBankClientAdapter.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090518082259353_fkwzbhxj\\Logic.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090518114646899_mrahkrxa\\N.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090518114718283_pckzzhrp\\M.class", true);
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\DoExternalProcessMinwon.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090515053147932_unktnred\\DoExternalProcessMinwon.class", true);
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\TryCatchFinallyClass.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514180437746_raelweyb\\TryCatchFinallyClass.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514193807307_xfbwelrk\\AlertDataManager.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514193807307_xfbwelrk\\AlertDataManager$LensedAlertDataQueryConnector.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514193807307_xfbwelrk\\AlertDataManager$DomainCallback.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514193807307_xfbwelrk\\AlertDataManager$AlertDataQueryConnector.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090514193807307_xfbwelrk\\AlertDataManager$AlertDataQueryConnector$1.class", true);
//			context.addClass("TestByte", "C:\\revjava\\remote\\data\\input\\20090517092633378_kmnzqbgb\\RobotsApplet.class", true);
			
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\e.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\e$1.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\e$2.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\e$3.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\e$4.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\y\\y\\option\\e$5.class", true);
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\SunGraphics2D.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\java2d\\SunGraphics2D.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\Channels$ReadableByteChannelImpl.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\java\\nio\\channels\\Channels$ReadableByteChannelImpl.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\jdbc\\odbc\\JdbcOdbcObject.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\PKCS7.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\security\\pkcs\\PKCS7.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\StepPattern.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\org\\apache\\xpath\\patterns\\StepPattern.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\ContextMatchStepPattern.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\org\\apache\\xpath\\patterns\\ContextMatchStepPattern.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\org\\apache\\xalan\\transformer\\TransformerImpl.class", true);
			
			
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\DatagramChannelImpl.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\nio\\ch\\DatagramChannelImpl.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\rapc\\net\\rim\\tools\\compiler\\d\\a5.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\java\\awt\\font\\TextLayout.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\com\\sun\\corba\\se\\internal\\iiop\\CDRInputStream_1_0.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\com\\sun\\java\\swing\\plaf\\motif\\MotifLookAndFeel.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\com\\sun\\imageio\\plugins\\jpeg\\JPEGMetadata.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\sun\\text\\resources\\DateFormatZoneData_sv.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\java\\nio\\channels\\FileChannel.class", true);
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\runtime1_4_2_03\\javax\\swing\\tree\\TreePath.class", true);
						
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\ICC_Transform.class", true);
			// block inlining!
//			context.addClass("TestByte", "C:\\Temp\\fernflower\\dec\\LdapReferralContext.class", true);
			
//			context.addSpace(new File("C:\\bahn\\fullcode\\lgsrv20batch\\bin\\"), true);
			
//			context.addSpace(new File("C:\\Temp\\tmp\\enum\\struct\\attr\\dest\\struct\\attr\\en\\"), true);
//			context.addSpace(new File("D:\\Nonbku\\workspace\\Fernflower\\bin\\struct\\attr\\en\\"), true);
			
//			context.addSpace(new File("D:\\Nonbku\\workspace\\InnerTest\\bin\\data\\"), true);
//			context.addSpace(new File("D:\\Nonbku\\workspace\\Fernflower\\bin\\struct\\attr\\en\\"), true);

//			context.addSpace(new File("D:\\Nonbku\\workspace\\Fernflower\\bin\\"), true);
//			context.addSpace(new File("C:\\bahn\\lfg\\geovis\\GeoVisStandalone\\bin\\"), true);
//			context.addSpace(new File("C:\\bahn\\lfg\\geovis\\GeoVisCommon\\bin\\"), true);
//			context.addSpace(new File("C:\\bahn\\lfg\\geovis\\GeoVisClient\\gvCl30Api\\build\\"), true);
//			context.addSpace(new File("C:\\bahn\\lfg\\geovis\\GeoVisClient\\gvCl40Ext\\build\\"), true);

//			context.addSpace(new File("C:\\bahn\\lfg\\geovis\\GeoVisClient\\gvCl30Api\\build\\de\\dbsystems\\geovis\\gui\\view\\"), true);
			
//			fl.structcontext = context;
		
//			fl.decompileContext(context, new File("D:\\Nonbku\\workspace_3.4\\JavaRuntime1_4_2_03\\src\\"));
//			fl.decompileContext(context, new File("C:\\bahn\\fullcode\\LfG\\server\\lgsrv20batch\\src\\java\\"));
//			fl.decompileContext(context, new File("D:\\Nonbku\\workspace\\InnerTest\\src\\struct\\attr\\en\\"));
//			fl.decompileContext(context, new File("D:\\Nonbku\\workspace\\Fernflower_dec\\src\\"));
//			fl.decompileContext(context, new File("C:\\Temp\\fernflower\\rapc_ff\\"));
//			fl.decompileContext(context, new File("C:\\Temp\\fernflower\\y_ff\\"));
//			fl.decompileContext(context, new File("C:\\Temp\\fernflower\\dec\\"));
//			fl.decompileContext(context, new File("C:\\Temp\\fernflower\\dec\\output\\"));
//			decompiler.decompileContext(new File("C:\\Oleg\\dec\\output\\"));
			decompiler.decompileContext(new File("D:\\temp_c\\workspace\\output\\"));
//			decompiler.decompileContext(new File("C:\\Temp\\fernflower\\dec\\output\\"));
//			decompiler.decompileContext(new File("C:\\Temp\\fernflower\\dec\\opera\\output\\dec\\"));
//			fl.decompileContext(context, new File("C:\\bahn\\lfg\\geovis_dec\\GeoVisStandalone\\starter\\src\\java\\"));
//			fl.decompileContext(context, new File("C:\\bahn\\lfg\\geovis_dec\\GeoVisCommon\\gvCom30Api\\src\\java\\"));
//			fl.decompileContext(context, new File("C:\\bahn\\lfg\\geovis_dec\\GeoVisClient\\gvCl30Api\\src\\java\\"));
//			fl.decompileContext(context, new File("C:\\bahn\\lfg\\geovis_dec\\GeoVisClient\\gvCl40Ext\\src\\java\\"));
			
//			MemoryMonitor.run = false;


			
			System.out.println("\n\nTime elapsed " + (new Date().getTime() - start.getTime())/1000);
			
//			System.out.println("1:\t"+Timer.getTime("general"));
//			System.out.println("2:\t"+Timer.getTime("generalnull"));
//			System.out.println("3:\t"+Timer.getTime("general1"));
			
			
//			System.out.println("1:\t"+Timer.getTime(10));
//			System.out.println("> 1:\t"+Timer.getTime(11));
//			System.out.println("=:\t"+Timer.getTime(12));
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}

}
