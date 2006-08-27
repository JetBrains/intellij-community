package com.siyeh.ig.naming;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.ui.DocumentAdapter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.RegExFormatter;
import com.siyeh.ig.RegExInputVerifier;
import com.siyeh.ig.ui.FormattedTextFieldMacFix;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.InternationalFormatter;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageNamingConventionInspection extends BaseGlobalInspection {

    private static final int DEFAULT_MIN_LENGTH = 3;
    private static final int DEFAULT_MAX_LENGTH = 16;
    /**
     * @noinspection PublicField
     */
    public String m_regex = "[a-z]*";      // this is public for the DefaultJDomExternalizer

    /**
     * @noinspection PublicField
     */
    public int m_minLength = DEFAULT_MIN_LENGTH;  // this is public for the DefaultJDomExternalizer

    /**
     * @noinspection PublicField
     */
    public int m_maxLength = DEFAULT_MAX_LENGTH;    // this is public for the DefaultJDomExternalizer

    private Pattern m_regexPattern = Pattern.compile(m_regex);


    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    @Nullable
    public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope analysisScope, InspectionManager inspectionManager, GlobalInspectionContext globalInspectionContext) {
        if (!(refEntity instanceof RefPackage)) {
            return null;
        }
        if (globalInspectionContext.isSuppressed(refEntity, getShortName())) {
            return null;
        }
        final String name = refEntity.getName();
        if (name == null) {
            return null;
        }

        final int length = name.length();
        if (length < m_minLength) {
            final String errorString =
                    InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.short", name);
            return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
        }
        if (length > m_maxLength) {
            final String errorString =
                    InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.long", name);
            return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
        }
        final Matcher matcher = m_regexPattern.matcher(name);
        if (matcher.matches()) {
            return null;
        } else {
            final String errorString =
                    InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.regex.mismatch", name, m_regex);
            return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
        }
    }

    public boolean isGraphNeeded() {
        return false;
    }

    public void readSettings(Element element) throws InvalidDataException {
        super.readSettings(element);
        m_regexPattern = Pattern.compile(m_regex);
    }

    private static final int REGEX_COLUMN_COUNT = 25;

    public JComponent createOptionsPanel() {
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);

        final JLabel patternLabel = new JLabel(InspectionGadgetsBundle.message("convention.pattern.option"));
        patternLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        final JLabel minLengthLabel = new JLabel(InspectionGadgetsBundle.message("convention.min.length.option"));
        minLengthLabel.setHorizontalAlignment(SwingConstants.TRAILING);
        final JLabel maxLengthLabel = new JLabel(InspectionGadgetsBundle.message("convention.max.length.option"));
        maxLengthLabel.setHorizontalAlignment(SwingConstants.TRAILING);

        final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
        numberFormat.setParseIntegerOnly(true);
        numberFormat.setMinimumIntegerDigits(1);
        numberFormat.setMaximumIntegerDigits(2);
        final InternationalFormatter formatter = new InternationalFormatter(numberFormat);
        formatter.setAllowsInvalid(false);
        formatter.setCommitsOnValidEdit(true);

        final JFormattedTextField minLengthField = new JFormattedTextField(formatter);
        final Font panelFont = panel.getFont();
        minLengthField.setFont(panelFont);
        minLengthField.setValue(m_minLength);
        minLengthField.setColumns(2);
        FormattedTextFieldMacFix.apply(minLengthField);

        final JFormattedTextField maxLengthField = new JFormattedTextField(formatter);
        maxLengthField.setFont(panelFont);
        maxLengthField.setValue(m_maxLength);
        maxLengthField.setColumns(2);
        FormattedTextFieldMacFix.apply(maxLengthField);

        final JFormattedTextField regexField = new JFormattedTextField(new RegExFormatter());
        regexField.setFont(panelFont);
        regexField.setValue(m_regexPattern);
        regexField.setColumns(REGEX_COLUMN_COUNT);
        regexField.setInputVerifier(new RegExInputVerifier());
        regexField.setFocusLostBehavior(JFormattedTextField.COMMIT);
        FormattedTextFieldMacFix.apply(regexField);
        final DocumentListener listener = new DocumentAdapter() {
            public void textChanged(DocumentEvent e) {
                try {
                    regexField.commitEdit();
                    minLengthField.commitEdit();
                    maxLengthField.commitEdit();
                    m_regexPattern = (Pattern) regexField.getValue();
                    m_regex = m_regexPattern.pattern();
                    m_minLength = ((Number) minLengthField.getValue()).intValue();
                    m_maxLength = ((Number) maxLengthField.getValue()).intValue();
                } catch (ParseException e1) {
                    // No luck this time
                }
            }
        };
        final Document regexDocument = regexField.getDocument();
        regexDocument.addDocumentListener(listener);
        final Document minLengthDocument = minLengthField.getDocument();
        minLengthDocument.addDocumentListener(listener);
        final Document maxLengthDocument = maxLengthField.getDocument();
        maxLengthDocument.addDocumentListener(listener);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(patternLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(regexField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.anchor = GridBagConstraints.EAST;
        panel.add(minLengthLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(minLengthField, constraints);

        constraints.gridx = 2;
        constraints.gridy = 1;
        constraints.anchor = GridBagConstraints.EAST;
        panel.add(maxLengthLabel, constraints);

        constraints.gridx = 3;
        constraints.gridy = 1;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(maxLengthField, constraints);

        return panel;
    }
}
